package com.peergeek.referral.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class IdentityClient {

	private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

	private final RestTemplate restTemplate;
	private final String identityServiceUrl;
	private final String gatewaySecret;

	public IdentityClient(
			@Value("${peergeek.services.identity-url:http://peergeek-identity-test:8091}") String identityServiceUrl,
			@Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySecret) {
		this.restTemplate = new RestTemplate();
		this.identityServiceUrl = identityServiceUrl;
		this.gatewaySecret = gatewaySecret;
	}

	public Optional<UserInfo> getUserById(Long userId) {
		String url = identityServiceUrl + "/api/v1/users/" + userId + "/public";
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Gateway-Secret", gatewaySecret);
		HttpEntity<Void> entity = new HttpEntity<>(headers);

		try {
			ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
			if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
				Map body = resp.getBody();
				String displayName = body.get("displayName") != null ? body.get("displayName").toString() : "User #" + userId;
				String username = body.get("username") != null ? body.get("username").toString() : "user" + userId;
				return Optional.of(new UserInfo(userId, displayName, username, null));
			}
		} catch (Exception ex) {
			log.warn("Failed to fetch user {} from identity service: {}", userId, ex.getMessage());
		}
		return Optional.of(new UserInfo(userId, "User #" + userId, "user" + userId, null));
	}

	public record UserInfo(Long id, String displayName, String username, String email) {}
}

