package com.peergeek.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.List;
import java.time.Instant;
import com.peergeek.config.StorageSettings;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class ObjectStorageService {
	private static final long MAX_RANGE_BYTES = 1_048_576L;
	private final S3Client client;
	private final S3Presigner presigner;
	private final S3Presigner internalPresigner;
	private final StorageSettings settings;

	public ObjectStorageService(
			S3Client client,
			@org.springframework.beans.factory.annotation.Qualifier("s3Presigner") S3Presigner presigner,
			@org.springframework.beans.factory.annotation.Qualifier("internalS3Presigner") S3Presigner internalPresigner,
			StorageSettings settings) {
		this.client = client;
		this.presigner = presigner;
		this.internalPresigner = internalPresigner;
		this.settings = settings;
	}

	public PresignedPut presignPut(String key, String contentType, long contentLength, Duration ttl) {
		if (!StringUtils.hasText(key) || !StringUtils.hasText(contentType) || contentLength <= 0) {
			throw new IllegalArgumentException("A key, content type, and positive content length are required.");
		}
		var request = PutObjectRequest.builder()
				.bucket(settings.bucket())
				.key(key)
				.contentType(contentType)
				.contentLength(contentLength)
				.build();
		var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.putObjectRequest(request)
				.build());
		Map<String, String> headers = signed.signedHeaders().entrySet().stream()
				.filter(entry -> !entry.getKey().equalsIgnoreCase("host"))
				.collect(LinkedHashMap::new,
						(map, entry) -> map.put(headerName(entry.getKey()), String.join(",", entry.getValue())),
						LinkedHashMap::putAll);
		headers.putIfAbsent("Content-Type", contentType);
		headers.putIfAbsent("Content-Length", Long.toString(contentLength));
		return new PresignedPut(URI.create(signed.url().toExternalForm()), headers, signed.expiration());
	}

	public PresignedGet presignGet(String key, String filename, Duration ttl, Disposition disposition) {
		return presignGet(presigner, key, filename, ttl, disposition);
	}

	public PresignedGet presignGetInternal(String key, String filename, Duration ttl, Disposition disposition) {
		return presignGet(internalPresigner, key, filename, ttl, disposition);
	}

	private PresignedGet presignGet(
			S3Presigner urlSigner, String key, String filename, Duration ttl, Disposition disposition) {
		if (!StringUtils.hasText(key)) {
			throw new IllegalArgumentException("A key is required.");
		}
		String safeFilename = sanitizeFilename(filename);
		ContentDisposition contentDisposition = disposition == Disposition.INLINE
				? ContentDisposition.inline().filename(safeFilename, java.nio.charset.StandardCharsets.UTF_8).build()
				: ContentDisposition.attachment().filename(safeFilename, java.nio.charset.StandardCharsets.UTF_8).build();
		var request = GetObjectRequest.builder()
				.bucket(settings.bucket())
				.key(key)
				.responseContentDisposition(contentDisposition.toString())
				.build();
		var signed = urlSigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.getObjectRequest(request)
				.build());
		return new PresignedGet(URI.create(signed.url().toExternalForm()), signed.expiration());
	}

	public Optional<ObjectHead> head(String key) {
		try {
			var response = client.headObject(HeadObjectRequest.builder()
					.bucket(settings.bucket()).key(key).build());
			return Optional.of(new ObjectHead(response.contentLength(), response.contentType()));
		} catch (NoSuchKeyException exception) {
			return Optional.empty();
		} catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				return Optional.empty();
			}
			throw exception;
		}
	}

	public byte[] readRange(String key, long from, long to) {
		if (from < 0 || to < from || to - from + 1 > MAX_RANGE_BYTES) {
			throw new IllegalArgumentException("The requested object range must be between 1 byte and 1 MiB.");
		}
		GetObjectRequest request = GetObjectRequest.builder()
				.bucket(settings.bucket())
				.key(key)
				.range("bytes=" + from + "-" + to)
				.build();
		try (InputStream stream = client.getObject(request)) {
			return stream.readNBytes(Math.toIntExact(to - from + 1));
		} catch (IOException exception) {
			throw new ObjectStorageException("Unable to read the requested object range.", exception);
		}
	}

	public InputStream openStream(String key) {
		return client.getObject(GetObjectRequest.builder()
				.bucket(settings.bucket())
				.key(key)
				.build());
	}

	public void putObject(String key, String contentType, long contentLength, InputStream stream) {
		client.putObject(PutObjectRequest.builder()
				.bucket(settings.bucket())
				.key(key)
				.contentType(contentType)
				.contentLength(contentLength)
				.build(),
				software.amazon.awssdk.core.sync.RequestBody.fromInputStream(stream, contentLength));
	}

	public void delete(String key) {
		client.deleteObject(DeleteObjectRequest.builder()
				.bucket(settings.bucket())
				.key(key)
				.build());
	}

	public ObjectPage listObjects(String prefix, String continuationToken) {
		var request = ListObjectsV2Request.builder()
				.bucket(settings.bucket())
				.prefix(prefix)
				.maxKeys(1000)
				.continuationToken(continuationToken)
				.build();
		var response = client.listObjectsV2(request);
		List<StoredObject> objects = response.contents().stream()
				.map(object -> new StoredObject(object.key(), object.lastModified()))
				.toList();
		return new ObjectPage(objects, response.nextContinuationToken());
	}

	private String sanitizeFilename(String filename) {
		if (!StringUtils.hasText(filename)) {
			return "resource";
		}
		String sanitized = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private String headerName(String name) {
		StringBuilder formatted = new StringBuilder(name.length());
		boolean upper = true;
		for (char character : name.toLowerCase(Locale.ROOT).toCharArray()) {
			if (character == '-') {
				formatted.append(character);
				upper = true;
			} else {
				formatted.append(upper ? Character.toUpperCase(character) : character);
				upper = false;
			}
		}
		return formatted.toString();
	}

	public record PresignedPut(URI url, Map<String, String> headers, Instant expiresAt) {
		public PresignedPut {
			headers = Map.copyOf(headers);
		}
	}

	public record PresignedGet(URI url, Instant expiresAt) {
	}

	public record ObjectHead(long sizeBytes, String contentType) {
	}

	public record StoredObject(String key, Instant lastModified) {
	}

	public record ObjectPage(List<StoredObject> objects, String continuationToken) {
		public ObjectPage {
			objects = List.copyOf(objects);
		}
	}

	public enum Disposition {
		INLINE,
		ATTACHMENT
	}
}
