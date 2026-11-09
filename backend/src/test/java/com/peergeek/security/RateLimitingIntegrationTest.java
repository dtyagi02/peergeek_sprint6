package com.peergeek.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.peergeek.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"peergeek.security.rate-limiting.auth-limit=3",
		"peergeek.security.rate-limiting.auth-window-seconds=60",
		"peergeek.security.rate-limiting.report-limit=2",
		"peergeek.security.rate-limiting.report-window-seconds=60"
})
class RateLimitingIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void securityHeadersArePresentOnResponses() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
				.andExpect(header().exists("Content-Security-Policy"));
	}

	@Test
	void authEndpointsEnforceRateLimitsAndReturnStandardErrorContract() throws Exception {
		String testIp = "198.51.100.42";
		String body = "{\"email\":\"nobody@example.com\",\"password\":\"BadPass123!\"}";

		// Requests 1, 2, 3 should pass through to auth handler (failing with 401 or bad request, but NOT 429)
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body)
							.header("X-Forwarded-For", testIp))
					.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		// Request 4 must exceed the configured limit of 3 and return 429
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.header("X-Forwarded-For", testIp))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "60"))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.error").value("Too Many Requests"))
				.andExpect(jsonPath("$.message").value("Too many requests. Please try again later."))
				.andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
	}

	@Test
	void reportEndpointsEnforceRateLimits() throws Exception {
		String testIp = "198.51.100.99";
		String body = "{\"category\":\"BROKEN_CONTENT\",\"details\":\"Broken video playback\",\"lessonId\":null}";

		// 2 requests allowed
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/courses/999/reports")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body)
							.header("X-Forwarded-For", testIp))
					.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		// 3rd request rejected with 429
		mockMvc.perform(post("/api/v1/courses/999/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.header("X-Forwarded-For", testIp))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}
}
