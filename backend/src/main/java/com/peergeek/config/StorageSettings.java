package com.peergeek.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("peergeek.storage")
public record StorageSettings(
	String endpoint,
	String publicEndpoint,
	String region,
	String accessKey,
	String secretKey,
	String bucket,
	boolean pathStyle,
	boolean createBucketIfMissing) {

	boolean hasStaticCredentials() {
		return accessKey != null && !accessKey.isBlank()
				&& secretKey != null && !secretKey.isBlank();
	}

	boolean hasPartialStaticCredentials() {
		boolean hasAccessKey = accessKey != null && !accessKey.isBlank();
		boolean hasSecretKey = secretKey != null && !secretKey.isBlank();
		return hasAccessKey != hasSecretKey;
	}
}
