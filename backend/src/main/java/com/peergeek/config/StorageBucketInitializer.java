package com.peergeek.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
public class StorageBucketInitializer {

	@Bean
	ApplicationRunner checkStorageAtStartup(
			S3Client s3Client, StorageSettings settings, Environment environment) {
		return args -> {
			if (environment.acceptsProfiles(Profiles.of("prod"))
					&& settings.createBucketIfMissing()) {
				throw new IllegalStateException(
						"STORAGE_CREATE_BUCKET_IF_MISSING must be false in the prod profile.");
			}
			try {
				s3Client.headBucket(HeadBucketRequest.builder().bucket(settings.bucket()).build());
			} catch (S3Exception exception) {
				if (exception.statusCode() == 404 && settings.createBucketIfMissing()) {
					try {
						s3Client.createBucket(
								CreateBucketRequest.builder().bucket(settings.bucket()).build());
						return;
					} catch (SdkException createException) {
						throw connectivityFailure(settings, statusCode(createException));
					}
				}
				throw connectivityFailure(settings, exception.statusCode());
			} catch (SdkException exception) {
				throw connectivityFailure(settings, -1);
			}
		};
	}

	private int statusCode(SdkException exception) {
		return exception instanceof S3Exception s3Exception ? s3Exception.statusCode() : -1;
	}

	private IllegalStateException connectivityFailure(StorageSettings settings, int statusCode) {
		String status = statusCode >= 0 ? " (HTTP " + statusCode + ")" : "";
		return new IllegalStateException(
				"S3 startup connectivity check failed for bucket '"
						+ settings.bucket()
						+ "' in region '"
						+ settings.region()
						+ "'"
						+ status
						+ ". Verify STORAGE_BUCKET, STORAGE_REGION, STORAGE_ENDPOINT, "
						+ "STORAGE_ACCESS_KEY/STORAGE_SECRET_KEY or the AWS default credentials, "
						+ "and the bucket permissions.");
	}
}
