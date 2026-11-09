package com.peergeek.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Component("objectStorage")
public class StorageHealthIndicator implements HealthIndicator {
	private final S3Client s3Client;
	private final StorageSettings settings;

	public StorageHealthIndicator(S3Client s3Client, StorageSettings settings) {
		this.s3Client = s3Client;
		this.settings = settings;
	}

	@Override
	public Health health() {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(settings.bucket()).build());
			return Health.up().withDetail("bucket", settings.bucket()).build();
		} catch (SdkException exception) {
			return Health.down(exception).withDetail("bucket", settings.bucket()).build();
		}
	}
}
