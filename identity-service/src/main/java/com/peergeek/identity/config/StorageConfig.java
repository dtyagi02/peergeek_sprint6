package com.peergeek.identity.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class StorageConfig {

	@Value("${peergeek.storage.endpoint:http://localhost:9000}")
	private String endpoint;

	@Value("${peergeek.storage.region:us-east-1}")
	private String region;

	@Value("${peergeek.storage.access-key:minioadmin}")
	private String accessKey;

	@Value("${peergeek.storage.secret-key:minioadmin}")
	private String secretKey;

	@Value("${peergeek.storage.path-style:true}")
	private boolean pathStyle;

	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKey, secretKey)))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(pathStyle)
						.build())
				.build();
	}
}

