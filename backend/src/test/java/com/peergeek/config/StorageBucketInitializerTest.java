package com.peergeek.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class StorageBucketInitializerTest {
	private final StorageBucketInitializer initializer = new StorageBucketInitializer();
	private final ApplicationArguments arguments = mock(ApplicationArguments.class);

	@Test
	void createsMissingBucketOnlyWhenEnabled() throws Exception {
		S3Client s3Client = mock(S3Client.class);
		when(s3Client.headBucket(any(HeadBucketRequest.class)))
				.thenThrow(S3Exception.builder().statusCode(404).build());

		initializer.checkStorageAtStartup(s3Client, settings(true), environment(false)).run(arguments);

		verify(s3Client).createBucket(any(CreateBucketRequest.class));
	}

	@Test
	void failsWithActionableMessageWhenBucketCheckFails() {
		S3Client s3Client = mock(S3Client.class);
		when(s3Client.headBucket(any(HeadBucketRequest.class)))
				.thenThrow(S3Exception.builder().statusCode(403).build());

		assertThatThrownBy(() ->
				initializer.checkStorageAtStartup(s3Client, settings(false), environment(false))
						.run(arguments))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("STORAGE_BUCKET")
				.hasMessageContaining("STORAGE_REGION")
				.hasMessageNotContaining("test-secret-key");

		verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
	}

	@Test
	void refusesBucketCreationInProductionBeforeConnecting() {
		S3Client s3Client = mock(S3Client.class);

		assertThatThrownBy(() ->
				initializer.checkStorageAtStartup(s3Client, settings(true), environment(true))
						.run(arguments))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("STORAGE_CREATE_BUCKET_IF_MISSING must be false");

		verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
		verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
	}

	private StorageSettings settings(boolean createBucketIfMissing) {
		return new StorageSettings(
				"", "", "us-east-1", "test-access-key", "test-secret-key",
				"test-bucket", false, createBucketIfMissing);
	}

	private Environment environment(boolean production) {
		Environment environment = mock(Environment.class);
		when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(production);
		return environment;
	}
}
