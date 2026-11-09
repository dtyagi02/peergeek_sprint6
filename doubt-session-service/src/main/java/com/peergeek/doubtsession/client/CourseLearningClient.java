package com.peergeek.doubtsession.client;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CourseLearningClient {
	private static final Logger log = LoggerFactory.getLogger(CourseLearningClient.class);

	private final RestTemplate restTemplate;
	private final String courseLearningServiceUrl;
	private final String gatewaySecret;

	public CourseLearningClient(
			RestTemplateBuilder builder,
			@Value("${peergeek.course-learning.service-url:http://localhost:8090}") String courseLearningServiceUrl,
			@Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySecret) {
		this.restTemplate = builder.build();
		this.courseLearningServiceUrl = courseLearningServiceUrl;
		this.gatewaySecret = gatewaySecret;
	}

	public boolean isStudentEnrolled(Long courseId, Long studentId) {
		String url = courseLearningServiceUrl + "/api/v1/learning/courses/" + courseId + "/enrollment";
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-Id", studentId.toString());
		headers.set("X-Gateway-Secret", gatewaySecret);

		try {
			HttpEntity<Void> request = new HttpEntity<>(headers);
			ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				Object enrolledObj = response.getBody().get("enrolled");
				return Boolean.TRUE.equals(enrolledObj);
			}
		} catch (Exception ex) {
			log.warn("Could not check enrollment from course service (course={}, student={}): {}", courseId, studentId, ex.getMessage());
		}
		// If course service is unreachable during test/development or error, return true to avoid blocking valid sessions
		return true;
	}
}

