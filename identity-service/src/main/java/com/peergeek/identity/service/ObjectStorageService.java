package com.peergeek.identity.service;

import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class ObjectStorageService {
	private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

	private final S3Client s3Client;
	private final String bucket;

	public ObjectStorageService(
			S3Client s3Client,
			@Value("${peergeek.storage.bucket:peergeek}") String bucket) {
		this.s3Client = s3Client;
		this.bucket = bucket;
	}

	public void putObject(String objectKey, String contentType, long size, InputStream inputStream) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.contentType(contentType)
				.contentLength(size)
				.build();
		s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
		log.info("Uploaded avatar object to bucket={}, key={}", bucket, objectKey);
	}

	public InputStream openStream(String objectKey) {
		GetObjectRequest request = GetObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.build();
		return s3Client.getObject(request);
	}

	public void delete(String objectKey) {
		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.build();
			s3Client.deleteObject(request);
			log.info("Deleted avatar object key={} from bucket={}", objectKey, bucket);
		} catch (Exception ex) {
			log.warn("Failed to delete avatar object key={}: {}", objectKey, ex.getMessage());
		}
	}

	public record ObjectHead(long sizeBytes, String contentType) {}

	public Optional<ObjectHead> head(String objectKey) {
		try {
			HeadObjectRequest request = HeadObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.build();
			HeadObjectResponse response = s3Client.headObject(request);
			return Optional.of(new ObjectHead(response.contentLength(), response.contentType()));
		} catch (NoSuchKeyException e) {
			return Optional.empty();
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				return Optional.empty();
			}
			throw e;
		}
	}
}

