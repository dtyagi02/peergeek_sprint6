package com.peergeek.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class S3ConfigTest {
	private final S3Config config = new S3Config();

	@Test
	void usesTheDefaultCredentialsProviderWhenStaticKeysAreUnset() {
		assertThat(config.credentialsProvider(settings("", "")))
				.isInstanceOf(DefaultCredentialsProvider.class);
	}

	@Test
	void usesStaticCredentialsWhenBothKeysAreProvided() {
		assertThat(config.credentialsProvider(settings("test-access-key", "test-secret-key")))
				.isInstanceOf(StaticCredentialsProvider.class);
	}

	@Test
	void rejectsOnlyOneStaticCredential() {
		assertThatThrownBy(() -> config.credentialsProvider(settings("test-access-key", "")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Set both STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY");
	}

	private StorageSettings settings(String accessKey, String secretKey) {
		return new StorageSettings("", "", "us-east-1", accessKey, secretKey, "test-bucket", false, false);
	}
}
