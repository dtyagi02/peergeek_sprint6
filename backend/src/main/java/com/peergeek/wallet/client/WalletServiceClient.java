package com.peergeek.wallet.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class WalletServiceClient {
	private static final Logger log = LoggerFactory.getLogger(WalletServiceClient.class);

	private final String serviceUrl;
	private final String internalSecret;
	private final boolean remoteEnabled;
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public WalletServiceClient(
			@Value("${wallet.service.url:http://localhost:8088}") String serviceUrl,
			@Value("${wallet.service.internal-secret:peergeek-internal-gateway-secret-key-change-in-prod}") String internalSecret,
			@Value("${wallet.service.remote-enabled:true}") boolean remoteEnabled,
			ObjectMapper objectMapper) {
		this.serviceUrl = (serviceUrl != null && serviceUrl.endsWith("/")) 
				? serviceUrl.substring(0, serviceUrl.length() - 1) 
				: (serviceUrl != null ? serviceUrl : "http://localhost:8088");
		this.internalSecret = internalSecret;
		this.remoteEnabled = remoteEnabled;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	public boolean isRemoteEnabled() {
		return remoteEnabled;
	}

	public BigDecimal getBalance(Long userId) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(serviceUrl + "/api/v1/internal/wallet/" + userId + "/balance"))
					.header("X-Gateway-Secret", internalSecret)
					.timeout(Duration.ofSeconds(5))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				JsonNode root = objectMapper.readTree(response.body());
				return new BigDecimal(root.path("balance").asText("0.00"));
			}
			log.error("Failed to fetch balance from remote wallet-service for userId={}: status={}", userId, response.statusCode());
			throw new RuntimeException("Remote wallet service returned status " + response.statusCode());
		} catch (Exception ex) {
			log.error("Error communicating with remote wallet-service: {}", ex.getMessage());
			throw new RuntimeException("Failed to reach remote wallet service: " + ex.getMessage(), ex);
		}
	}

	public void reserveFunds(Long userId, BigDecimal amount, String referenceType, String referenceId, String description) {
		postInternal("/api/v1/internal/wallet/reservations", Map.of(
				"userId", userId,
				"amount", amount,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description != null ? description : ""
		));
	}

	public void releaseReservedFunds(Long userId, BigDecimal amount, String referenceType, String referenceId, String description) {
		postInternal("/api/v1/internal/wallet/release", Map.of(
				"userId", userId,
				"amount", amount,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description != null ? description : ""
		));
	}

	public void settleSessionPayment(Long studentUserId, Long instructorUserId, BigDecimal reservedAmount, BigDecimal chargeAmount, String referenceType, String referenceId, String description) {
		postInternal("/api/v1/internal/wallet/settlements", Map.of(
				"studentUserId", studentUserId,
				"instructorUserId", instructorUserId,
				"reservedAmount", reservedAmount,
				"chargeAmount", chargeAmount,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description != null ? description : ""
		));
	}

	public void credit(Long userId, BigDecimal amount, String type, String referenceType, String referenceId, String description) {
		postInternal("/api/v1/internal/wallet/credit", Map.of(
				"userId", userId,
				"amount", amount,
				"type", type,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description != null ? description : ""
		));
	}

	public void batchReferralReward(Long inviterUserId, BigDecimal inviterAmount, Long newUserId, BigDecimal newUserAmount, String referralId, String inviterDesc, String newUserDesc) {
		postInternal("/api/v1/internal/wallet/rewards/referral", Map.of(
				"inviterUserId", inviterUserId,
				"inviterRewardAmount", inviterAmount,
				"newUserId", newUserId,
				"newUserRewardAmount", newUserAmount,
				"referralId", referralId,
				"inviterDescription", inviterDesc != null ? inviterDesc : "",
				"newUserDescription", newUserDesc != null ? newUserDesc : ""
		));
	}

	private void postInternal(String path, Object payload) {
		try {
			String json = objectMapper.writeValueAsString(payload);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(serviceUrl + path))
					.header("Content-Type", "application/json")
					.header("X-Gateway-Secret", internalSecret)
					.timeout(Duration.ofSeconds(5))
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return;
			}
			log.error("Remote wallet-service error at {}: status={} body={}", path, response.statusCode(), response.body());
			throw new RuntimeException("Remote wallet service call failed (" + response.statusCode() + "): " + response.body());
		} catch (Exception ex) {
			log.error("Failed to post to remote wallet service at {}: {}", path, ex.getMessage());
			throw new RuntimeException("Failed to call remote wallet service: " + ex.getMessage(), ex);
		}
	}
}

