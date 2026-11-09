package com.peergeek.readiness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import com.peergeek.config.ReadinessSettings;
import com.peergeek.file.ResourceContentReader;
import com.peergeek.file.ResourceType;
import com.peergeek.readiness.video.VideoResourceProbeService;
import org.apache.pdfbox.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeepReadinessRules implements ReadinessRule {
	private static final Logger LOGGER = LoggerFactory.getLogger(DeepReadinessRules.class);
	private final ResourceContentReader content;
	private final ReadinessSettings settings;
	private final VideoResourceProbeService videos;
	private final SafeLinkChecker links;

	public DeepReadinessRules(ResourceContentReader content, ReadinessSettings settings,
			VideoResourceProbeService videos, SafeLinkChecker links) {
		this.content = content;
		this.settings = settings;
		this.videos = videos;
		this.links = links;
	}

	public List<ReadinessIssue> evaluate(ReadinessContext context) {
		List<ReadinessIssue> issues = new ArrayList<>();
		if (context.metadata().thumbnailResourceId() != null) {
			try {
				if (!content.statObject(context.metadata().thumbnailResourceId()).exists()) {
					issues.add(resourceIssue("COURSE_THUMBNAIL_MISSING",
							ReadinessIssue.Severity.BLOCKING_ERROR, null,
							context.metadata().thumbnailResourceId(),
							"The course thumbnail object is missing.",
							"Upload a replacement course thumbnail.", null));
				}
			} catch (RuntimeException exception) {
				issues.add(resourceIssue("COURSE_THUMBNAIL_UNAVAILABLE",
						ReadinessIssue.Severity.BLOCKING_ERROR, null,
						context.metadata().thumbnailResourceId(),
						"The course thumbnail could not be checked.",
						"Retry the readiness check or upload a replacement thumbnail.", null));
			}
		}
		for (var lesson : context.resources()) {
			for (var resource : lesson.resources()) {
				if (resource.status() != com.peergeek.file.ResourceStatus.READY) continue;
				switch (resource.type()) {
					case LINK -> checkLink(issues, lesson.lessonId(), resource.id(), resource.externalUrl());
					case VIDEO -> checkVideo(issues, lesson.lessonId(), resource.id(), resource.checksumSha256());
					case PDF, DOCUMENT, IMAGE -> checkFile(issues, lesson.lessonId(), resource);
					default -> throw new IllegalStateException("Unsupported resource type: " + resource.type());
				}
			}
		}
		return List.copyOf(issues);
	}

	private void checkLink(List<ReadinessIssue> issues, long lessonId, long resourceId, String url) {
		SafeLinkChecker.Result result = links.check(resourceId, url);
		if (result.outcome() == SafeLinkChecker.Outcome.AVAILABLE) return;
		ReadinessIssue.Severity severity = result.outcome() == SafeLinkChecker.Outcome.UNAVAILABLE
				? ReadinessIssue.Severity.WARNING : ReadinessIssue.Severity.BLOCKING_ERROR;
		issues.add(resourceIssue("LINK_" + result.outcome().name(), severity, lessonId, resourceId,
				"External resource link check returned " + result.outcome().name().toLowerCase() + ".",
				"Verify that the URL is public, secure, and reachable.", result.httpStatus()));
	}

	private void checkVideo(List<ReadinessIssue> issues, long lessonId, long resourceId, String checksum) {
		if (!settings.getVideo().isProbeRequired()) {
			issues.add(resourceIssue("VIDEO_PROBE_DISABLED", ReadinessIssue.Severity.WARNING,
					lessonId, resourceId, "Video validation is disabled by server configuration.",
					"Enable READINESS_VIDEO_PROBE_REQUIRED to validate this video.", null));
			return;
		}
		if (checksum == null || !checksum.matches("[a-fA-F0-9]{64}")) {
			issues.add(resourceIssue("VIDEO_CHECKSUM_MISSING", ReadinessIssue.Severity.BLOCKING_ERROR,
					lessonId, resourceId, "The video has no valid SHA-256 checksum.",
					"Reprocess or upload the video again.", null));
			return;
		}
		var outcome = videos.probe(resourceId, checksum);
		if (outcome.valid()) return;
		String errorCode = outcome.errorCode();
		issues.add(resourceIssue(errorCode, ReadinessIssue.Severity.BLOCKING_ERROR, lessonId, resourceId,
				errorCode.equals("VIDEO_INVALID")
						? "The video is malformed, has no video stream, or has no positive duration."
						: "The video could not be checked because ffprobe or object access is unavailable.",
				"Replace the video with a valid supported video file.", null));
	}

	private void checkFile(List<ReadinessIssue> issues, long lessonId,
			com.peergeek.file.LessonResourceQueryService.CourseResourceSnapshot resource) {
		try {
			ResourceContentReader.ObjectStat stat = content.statObject(resource.id());
			if (!stat.exists()) {
				issues.add(resourceIssue("RESOURCE_OBJECT_MISSING", ReadinessIssue.Severity.BLOCKING_ERROR,
						lessonId, resource.id(), "The stored resource object is missing.",
						"Upload the resource again.", null));
				return;
			}
			if (resource.sizeBytes() != null && !resource.sizeBytes().equals(stat.sizeBytes())) {
				issues.add(resourceIssue("RESOURCE_SIZE_MISMATCH", ReadinessIssue.Severity.BLOCKING_ERROR,
						lessonId, resource.id(), "Stored object size does not match resource metadata.",
						"Upload the resource again.", null));
				return;
			}
			verifyChecksum(resource.id(), resource.checksumSha256(), stat.sizeBytes());
			validateFile(resource.id(), resource.type(), stat.sizeBytes());
		} catch (RuntimeException | IOException exception) {
			LOGGER.warn("Readiness content validation failed resourceId={} exceptionType={}",
					resource.id(), exception.getClass().getSimpleName());
			issues.add(resourceIssue("RESOURCE_CONTENT_INVALID", ReadinessIssue.Severity.BLOCKING_ERROR,
					lessonId, resource.id(), "The resource content could not be validated.",
					"Replace the file with a valid supported document.", null));
		}

	}

	private void validateFile(long resourceId, ResourceType type, long size) throws IOException {
		if (type == ResourceType.PDF) {
			if (size > settings.getPdf().getMaxBytes()) {
				throw new IOException("PDF exceeds configured validation limit.");
			}
			byte[] bytes = readBounded(resourceId, settings.getPdf().getMaxBytes());
			try (var document = Loader.loadPDF(bytes)) {
				if (document.getNumberOfPages() < 1) throw new IOException("PDF has no pages.");
			}
		} else if (type == ResourceType.DOCUMENT) {
			validateOoxml(resourceId, size);
		} else if (type == ResourceType.IMAGE) {
			validateImage(resourceId);
		}
	}

	private void validateOoxml(long resourceId, long compressedSize) throws IOException {
		int entries = 0;
		long expanded = 0;
		boolean contentTypes = false;
		boolean officeDocument = false;
		try (InputStream raw = content.openStream(resourceId);
				ZipInputStream zip = new ZipInputStream(raw)) {
			for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null; zip.closeEntry()) {
				if (++entries > settings.getOoxml().getMaxEntries()) {
					throw new IOException("Document contains too many entries.");
				}
				if (entry.getName().startsWith("/") || entry.getName().contains("../")
						|| entry.getName().contains("\\") || entry.getName().indexOf('\0') >= 0) {
					throw new IOException("Document contains an invalid entry path.");
				}
				contentTypes |= entry.getName().equals("[Content_Types].xml");
				officeDocument |= entry.getName().equals("word/document.xml")
						|| entry.getName().equals("xl/workbook.xml")
						|| entry.getName().equals("ppt/presentation.xml");
				byte[] buffer = new byte[8192];
				for (int read; (read = zip.read(buffer)) != -1; ) {
					expanded += read;
					if (expanded > settings.getOoxml().getMaxExpandedBytes()) {
						throw new IOException("Document expands beyond the configured limit.");
					}
					if (compressedSize > 0
							&& (double) expanded / compressedSize
									> settings.getOoxml().getMaxCompressionRatio()) {
						throw new IOException("Document compression ratio exceeds the configured limit.");
					}
				}
			}
		}
		if (!contentTypes || !officeDocument) throw new IOException("Document is not a supported OOXML file.");
	}

	private void verifyChecksum(long resourceId, String expected, long size) throws IOException {
		if (expected == null || size > settings.getIntegrity().getMaxRehashBytes()) return;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = content.openStream(resourceId)) {
				byte[] buffer = new byte[16 * 1024];
				for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
			}
			if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected)) {
				throw new IOException("Resource checksum does not match its metadata.");
			}
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private void validateImage(long resourceId) throws IOException {
		try (InputStream raw = content.openStream(resourceId);
				ImageInputStream imageInput = ImageIO.createImageInputStream(raw)) {
			if (imageInput == null) throw new IOException("Invalid image stream.");
			var readers = ImageIO.getImageReaders(imageInput);
			if (!readers.hasNext()) throw new IOException("Unsupported image format.");
			ImageReader reader = readers.next();
			try {
				reader.setInput(imageInput, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > settings.getImage().getMaxPixels()) {
					throw new IOException("Image dimensions exceed the configured limit.");
				}
			} finally {
				reader.dispose();
			}
		}
	}

	private byte[] readBounded(long resourceId, long maxBytes) throws IOException {
		try (InputStream stream = content.openStream(resourceId);
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			long count = 0;
			for (int read; (read = stream.read(buffer)) != -1; ) {
				count += read;
				if (count > maxBytes) throw new IOException("Resource exceeds the configured validation limit.");
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	private ReadinessIssue resourceIssue(String code, ReadinessIssue.Severity severity, Long lessonId,
			long resourceId, String message, String fix, Integer httpStatus) {
		var details = httpStatus == null ? java.util.Map.<String, Object>of()
				: java.util.Map.<String, Object>of("httpStatus", httpStatus);
		return new ReadinessIssue(code, severity, ReadinessIssue.Scope.RESOURCE, null, lessonId, resourceId,
				null, null, null, message, fix, details);
	}

	@Override
	public String code() {
		return "DEEP";
	}

	@Override
	public RulePhase phase() {
		return RulePhase.DEEP;
	}
}
