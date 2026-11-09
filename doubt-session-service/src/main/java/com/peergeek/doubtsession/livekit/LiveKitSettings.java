package com.peergeek.doubtsession.livekit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "peergeek.livekit")
public class LiveKitSettings {
	private static final Logger log = LoggerFactory.getLogger(LiveKitSettings.class);

	private String url = "http://localhost:7880";
	private String apiKey = "devkey";
	private String apiSecret = "secret";

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getApiSecret() {
		return apiSecret;
	}

	public void setApiSecret(String apiSecret) {
		this.apiSecret = apiSecret;
	}
}

