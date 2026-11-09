package com.peergeek.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfig {

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppSettings settings) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(settings.corsAllowedOrigins());
		cors.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Request-Id"));
		cors.setExposedHeaders(java.util.List.of("X-Request-Id"));
		cors.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", cors);
		source.registerCorsConfiguration("/ws/**", cors);
		return source;
	}

	@Bean
	CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
		return new CorsFilter(corsConfigurationSource);
	}
}
