package com.peergeek.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.config.ResourceSettings;
import com.peergeek.course.CourseThumbnailAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ResourceProcessingWorker {
	private static final Logger log = LoggerFactory.getLogger(ResourceProcessingWorker.class);
	private static final int SAMPLE_BYTES = 65_536;
	private static final int MAX_ZIP_ENTRIES = 1_024;
	private final ResourceRepository resources;
	private final ObjectStorageService storage;
	private final ResourceSettings settings;
	private final AuditService audit;
	private final CourseThumbnailAccessor thumbnails;
	private final ResourceEventPublisher events;
	private final TransactionTemplate transactions;

	public ResourceProcessingWorker(
			ResourceRepository resources,
			ObjectStorageService storage,
			ResourceSettings settings,
			AuditService audit,
			CourseThumbnailAccessor thumbnails,
			ResourceEventPublisher events,
			org.springframework.transaction.PlatformTransactionManager transactionManager) {
		this.resources = resources;
		this.storage = storage;
		this.settings = settings;
		this.audit = audit;
		this.thumbnails = thumbnails;
		this.events = events;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public void process(long resourceId) {
		ProcessingSnapshot snapshot = transactions.execute(status -> resources.findByIdAndDeletedAtIsNull(resourceId)
				.filter(resource -> resource.getStatus() == ResourceStatus.PROCESSING)
				.map(resource -> {
					resource.beginProcessingAttempt();
					return new ProcessingSnapshot(resource.getId(), resource.getCourseId(), resource.getLessonId(),
							resource.getUploadedBy().getId(), resource.getObjectKey(), resource.getOriginalFilename(),
							resource.getMimeType(), resource.getSizeBytes(), resource.getType(), resource.getPurpose());
				}).orElse(null));
		if (snapshot == null) {
			return;
		}

		ProcessingResult result;
		try {
			result = inspect(snapshot);
		} catch (ObjectMissingException exception) {
			result = ProcessingResult.failed("UPLOAD_MISSING");
		} catch (ContentMismatchException exception) {
			result = ProcessingResult.failed("CONTENT_TYPE_MISMATCH");
		} catch (SizeMismatchException exception) {
			result = ProcessingResult.failed("SIZE_MISMATCH");
		} catch (Exception exception) {
			log.warn("Resource processing failed resourceId={} exceptionType={}",
					resourceId, exception.getClass().getName());
			result = ProcessingResult.failed("PROCESSING_ERROR");
		}

		ProcessingResult completed = result;
		transactions.executeWithoutResult(status -> resources.findByIdAndDeletedAtIsNull(resourceId)
				.filter(resource -> resource.getStatus() == ResourceStatus.PROCESSING)
				.ifPresent(resource -> {
					if (completed.failureReason() == null) {
						resource.markReady(completed.checksumSha256(), completed.sizeBytes());
						if (snapshot.purpose() == ResourcePurpose.COURSE_THUMBNAIL) {
							Long previousId = thumbnails.getThumbnailResourceId(snapshot.courseId()).orElse(null);
							thumbnails.setThumbnailResourceId(snapshot.courseId(), resourceId);
							if (previousId != null && previousId != resourceId) {
								resources.findByIdAndDeletedAtIsNull(previousId).ifPresent(previous -> {
									previous.softDelete(java.time.Instant.now());
									events.publishAfterCommit(snapshot.courseId(), null,
											previousId, ResourceChangeType.DELETED);
								});
							}
							Map<String, Object> details = new java.util.HashMap<>();
							details.put("courseId", snapshot.courseId());
							details.put("resourceId", resourceId);
							if (previousId != null) {
								details.put("previousResourceId", previousId);
							}
							audit.record(new AuditEvent(snapshot.uploadedBy(),
									ResourceAuditActions.COURSE_THUMBNAIL_CHANGED,
									"course", Long.toString(snapshot.courseId()), details));
						} else {
							audit.record(new AuditEvent(snapshot.uploadedBy(), ResourceAuditActions.RESOURCE_ADDED,
									"resource", Long.toString(resourceId),
									Map.of("courseId", snapshot.courseId(), "type", snapshot.type().name(),
											"sizeBytes", completed.sizeBytes(),
											"filename", snapshot.filename())));
						}
						events.publishAfterCommit(snapshot.courseId(), snapshot.lessonId(),
								resourceId, ResourceChangeType.READY);
					} else {
						resource.markFailed(completed.failureReason());
						events.publishAfterCommit(snapshot.courseId(), snapshot.lessonId(),
								resourceId, ResourceChangeType.FAILED);
					}
				}));
	}

	private ProcessingResult inspect(ProcessingSnapshot resource) throws IOException {
		MessageDigest digest = sha256();
		ByteArrayOutputStream sample = new ByteArrayOutputStream(SAMPLE_BYTES);
		String extension = extension(resource.filename());
		StreamingUtf8Validator textValidator = resource.type() == ResourceType.DOCUMENT
				&& (extension.equals("txt") || extension.equals("csv"))
						? new StreamingUtf8Validator()
						: null;
		long bytesRead = 0;
		byte[] buffer = new byte[64 * 1024];
		try (InputStream input = storage.openStream(resource.objectKey())) {
			int length;
			while ((length = input.read(buffer)) != -1) {
				bytesRead += length;
				if (bytesRead > resource.declaredSize()) {
					throw new SizeMismatchException();
				}
				digest.update(buffer, 0, length);
				if (textValidator != null) {
					textValidator.accept(buffer, length);
				}
				if (sample.size() < SAMPLE_BYTES) {
					sample.write(buffer, 0, Math.min(length, SAMPLE_BYTES - sample.size()));
				}
			}
		} catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException exception) {
			throw new ObjectMissingException();
		} catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
			if (exception.statusCode() == 404) {
				throw new ObjectMissingException();
			}
			throw exception;
		}
		if (textValidator != null) {
			textValidator.finish();
		}
		if (bytesRead != resource.declaredSize() || bytesRead == 0) {
			throw new SizeMismatchException();
		}
		byte[] initialBytes = sample.toByteArray();
		validateContent(resource, initialBytes);
		return ProcessingResult.ready(HexFormat.of().formatHex(digest.digest()), bytesRead);
	}

	private void validateContent(ProcessingSnapshot resource, byte[] sample) throws IOException {
		String extension = extension(resource.filename());
		boolean valid = switch (resource.type()) {
			case PDF -> resource.mimeType().equals("application/pdf")
					&& startsWith(sample, new byte[] {'%', 'P', 'D', 'F', '-'});
			case IMAGE -> isValidImage(resource.mimeType(), sample);
			case VIDEO -> isValidVideo(resource.mimeType(), sample);
			case DOCUMENT -> validateDocument(extension, resource.mimeType(), sample, resource.objectKey());
			case LINK -> false;
		};
		if (!valid) {
			throw new ContentMismatchException();
		}
	}

	private boolean validateDocument(String extension, String mimeType, byte[] sample, String key) throws IOException {
		return switch (extension) {
			case "txt", "csv" -> {
				String expectedMimeType = extension.equals("txt") ? "text/plain" : "text/csv";
				yield mimeType.equals(expectedMimeType) && containsNoNullBytes(sample);
			}
			case "docx" -> mimeType.equals(
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document")
					&& hasZipEntry(key, "word/document.xml");
			case "pptx" -> mimeType.equals(
					"application/vnd.openxmlformats-officedocument.presentationml.presentation")
					&& hasZipEntry(key, "ppt/presentation.xml");
			case "xlsx" -> mimeType.equals(
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
					&& hasZipEntry(key, "xl/workbook.xml");
			default -> false;
		};
	}

	private boolean hasZipEntry(String key, String expectedEntry) throws IOException {
		int scanned = 0;
		try (BoundedZipInputStream zip = new BoundedZipInputStream(
				storage.openStream(key), settings.maxOoxmlExpandedBytes())) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (++scanned > MAX_ZIP_ENTRIES) {
					return false;
				}
				if (!entry.isDirectory() && entry.getName().equals(expectedEntry)) {
					return zip.read() != -1;
				}
			}
		} catch (BoundedZipInputStream.ExpandedLimitException exception) {
			return false;
		}
		return false;
	}

	private boolean containsNoNullBytes(byte[] bytes) {
		for (byte value : bytes) {
			if (value == 0) {
				return false;
			}
		}
		return true;
	}

	private boolean isValidImage(String mimeType, byte[] sample) {
		return switch (mimeType) {
			case "image/png" -> startsWith(sample,
					new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
			case "image/jpeg" -> startsWith(sample, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
			case "image/gif" -> startsWith(sample, "GIF87a".getBytes(StandardCharsets.US_ASCII))
					|| startsWith(sample, "GIF89a".getBytes(StandardCharsets.US_ASCII));
			case "image/webp" -> sample.length >= 12
					&& startsWith(sample, "RIFF".getBytes(StandardCharsets.US_ASCII))
					&& new String(sample, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
			default -> false;
		};
	}

	private boolean isValidVideo(String mimeType, byte[] sample) {
		return switch (mimeType) {
			case "video/mp4" -> sample.length >= 8
					&& new String(sample, 4, 4, StandardCharsets.US_ASCII).equals("ftyp");
			case "video/webm" -> startsWith(sample, new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});
			default -> false;
		};
	}

	private boolean startsWith(byte[] bytes, byte[] prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (bytes[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private String extension(String filename) {
		int dot = filename == null ? -1 : filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
	}

	private MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available in this runtime.", exception);
		}
	}

	private record ProcessingSnapshot(
			long id, long courseId, Long lessonId, long uploadedBy, String objectKey,
			String filename, String mimeType, long declaredSize, ResourceType type, ResourcePurpose purpose) {
	}

	private record ProcessingResult(String checksumSha256, long sizeBytes, String failureReason) {
		static ProcessingResult ready(String checksum, long size) {
			return new ProcessingResult(checksum, size, null);
		}
		static ProcessingResult failed(String reason) {
			return new ProcessingResult(null, 0, reason);
		}
	}

	private static final class ObjectMissingException extends IOException {
	}

	private static final class ContentMismatchException extends IOException {
	}

	private static final class SizeMismatchException extends IOException {
	}

	private static final class BoundedZipInputStream extends ZipInputStream {
		private final long limit;
		private long expanded;
		private final byte[] singleByteBuffer = new byte[1];

		BoundedZipInputStream(InputStream input, long limit) {
			super(input);
			this.limit = limit;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int count = super.read(buffer, offset, length);
			if (count > 0 && (expanded += count) > limit) {
				throw new ExpandedLimitException();
			}
			return count;
		}

		@Override
		public int read() throws IOException {
			int count = read(singleByteBuffer, 0, 1);
			return count == -1 ? -1 : Byte.toUnsignedInt(singleByteBuffer[0]);
		}

		private static final class ExpandedLimitException extends IOException {
		}
	}

	private static final class StreamingUtf8Validator {
		private final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		private ByteBuffer pending = ByteBuffer.allocate(4);
		private boolean finished;

		StreamingUtf8Validator() {
			pending.flip();
		}

		void accept(byte[] bytes, int length) throws ContentMismatchException {
			if (finished) {
				throw new IllegalStateException("Text validation has already completed.");
			}
			ByteBuffer input = ByteBuffer.allocate(pending.remaining() + length);
			input.put(pending);
			input.put(bytes, 0, length);
			input.flip();
			checkNullBytes(input);
			CharBuffer output = CharBuffer.allocate(input.remaining());
			if (decoder.decode(input, output, false).isError()) {
				throw new ContentMismatchException();
			}
			pending = ByteBuffer.allocate(4);
			pending.put(input);
			pending.flip();
		}

		void finish() throws ContentMismatchException {
			ByteBuffer input = pending;
			CharBuffer output = CharBuffer.allocate(Math.max(1, input.remaining()));
			if (decoder.decode(input, output, true).isError() || decoder.flush(output).isError()) {
				throw new ContentMismatchException();
			}
			finished = true;
		}

		private void checkNullBytes(ByteBuffer input) throws ContentMismatchException {
			for (int index = input.position(); index < input.limit(); index++) {
				if (input.get(index) == 0) {
					throw new ContentMismatchException();
				}
			}
		}
	}
}
