package com.peergeek.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("peergeek.redis")
public record RedisSettings(String host, int port, Duration connectTimeout, Duration commandTimeout) {
}
