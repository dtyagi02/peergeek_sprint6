package com.peergeek.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("peergeek.app")
public record AppSettings(List<String> corsAllowedOrigins) {
	public AppSettings {
		corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
	}
}
