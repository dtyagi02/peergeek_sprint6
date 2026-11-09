package com.peergeek.doubtsession;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "peergeek.livekit")
public class LiveKitSettings {
	private static final Logger log = LoggerFactory.getLogger(LiveKitSettings.class);

	private String url;
	private String apiKey;
	private String apiSecret;

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

	@PostConstruct
	public void init() {
		// 1. Try system env
		if (url == null || url.isBlank()) {
			url = System.getenv("LIVEKIT_URL");
		}
		if (apiKey == null || apiKey.isBlank()) {
			apiKey = System.getenv("LIVEKIT_API_KEY");
		}
		if (apiSecret == null || apiSecret.isBlank()) {
			apiSecret = System.getenv("LIVEKIT_API_SECRET");
		}

		// 2. Fallback: read from .env if present in root or parent directories
		if (isBlank(url) || isBlank(apiKey) || isBlank(apiSecret)) {
			Map<String, String> dotEnv = loadDotEnv();
			if (isBlank(url)) url = dotEnv.get("LIVEKIT_URL");
			if (isBlank(apiKey)) apiKey = dotEnv.get("LIVEKIT_API_KEY");
			if (isBlank(apiSecret)) apiSecret = dotEnv.get("LIVEKIT_API_SECRET");
		}

		// 3. Fallback defaults for dev/testing so server does not crash if livekit not configured
		if (isBlank(url)) url = "wss://demo.livekit.cloud";
		if (isBlank(apiKey)) apiKey = "devkey";
		if (isBlank(apiSecret)) apiSecret = "secret";

		log.info("LiveKit configured with url={}, apiKey={}, apiSecret=[PROTECTED]",
				url, apiKey != null && !apiKey.isBlank() ? apiKey.substring(0, Math.min(apiKey.length(), 6)) + "..." : "none");
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private Map<String, String> loadDotEnv() {
		Map<String, String> map = new HashMap<>();
		File[] candidates = new File[] {
				new File(".env"),
				new File("../.env"),
				new File("../../.env")
		};
		for (File file : candidates) {
			if (file.exists() && file.isFile()) {
				try {
					List<String> lines = Files.readAllLines(file.toPath());
					for (String line : lines) {
						String trimmed = line.trim();
						if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
						int eq = trimmed.indexOf('=');
						if (eq > 0) {
							String key = trimmed.substring(0, eq).trim();
							String val = trimmed.substring(eq + 1).trim();
							if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
								val = val.substring(1, val.length() - 1);
							}
							map.put(key, val);
						}
					}
					break;
				} catch (Exception ex) {
					log.debug("Could not parse .env file {}: {}", file, ex.getMessage());
				}
			}
		}
		return map;
	}
}
