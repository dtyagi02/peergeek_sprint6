package com.peergeek.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3Config {

	@Bean
	S3Client s3Client(StorageSettings settings) {
		if (settings.region() == null || settings.region().isBlank()) {
			throw new IllegalStateException("STORAGE_REGION must be set for S3 storage.");
		}
		if (settings.bucket() == null || settings.bucket().isBlank()) {
			throw new IllegalStateException("STORAGE_BUCKET must be set for S3 storage.");
		}
		AwsCredentialsProvider credentialsProvider = credentialsProvider(settings);

		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(settings.region()))
				.credentialsProvider(credentialsProvider)
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(settings.pathStyle())
						.build());
		if (settings.endpoint() != null && !settings.endpoint().isBlank()) {
			builder.endpointOverride(java.net.URI.create(settings.endpoint()));
		}
		return builder.build();
	}

	@Bean
	S3Presigner s3Presigner(StorageSettings settings) {
		return presigner(settings, chooseEndpoint(settings.publicEndpoint(), settings.endpoint()));
	}

	@Bean
	@Qualifier("internalS3Presigner")
	S3Presigner internalS3Presigner(StorageSettings settings) {
		return presigner(settings, settings.endpoint());
	}

	private S3Presigner presigner(StorageSettings settings, String endpoint) {
		if (settings.region() == null || settings.region().isBlank()) {
			throw new IllegalStateException("STORAGE_REGION must be set for S3 storage.");
		}
		if (settings.bucket() == null || settings.bucket().isBlank()) {
			throw new IllegalStateException("STORAGE_BUCKET must be set for S3 storage.");
		}
		S3Presigner.Builder builder = S3Presigner.builder()
				.region(Region.of(settings.region()))
				.credentialsProvider(credentialsProvider(settings))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(settings.pathStyle())
						.build());
		if (endpoint == null || endpoint.isBlank()) {
			return builder.build();
		}
		builder.endpointOverride(java.net.URI.create(endpoint));
		return builder.build();
	}

	private String chooseEndpoint(String preferred, String fallback) {
		return preferred == null || preferred.isBlank() ? fallback : preferred;
	}

	AwsCredentialsProvider credentialsProvider(StorageSettings settings) {
		if (settings.hasPartialStaticCredentials()) {
			throw new IllegalStateException(
					"Set both STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY, or leave both unset to use the AWS default credentials provider.");
		}
		return settings.hasStaticCredentials()
				? StaticCredentialsProvider.create(
						AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()))
				: DefaultCredentialsProvider.create();
	}
}
