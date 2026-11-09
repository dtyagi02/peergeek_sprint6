package com.peergeek.doubtsession.client;

import java.math.BigDecimal;
import java.util.Map;
import com.peergeek.doubtsession.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class WalletServiceClient {
	private static final Logger log = LoggerFactory.getLogger(WalletServiceClient.class);

	private final RestTemplate restTemplate;
	private final String walletServiceUrl;
	private final String gatewaySecret;

	public WalletServiceClient(
			RestTemplateBuilder builder,
			@Value("${peergeek.wallet.service-url:http://localhost:8088}") String walletServiceUrl,
			@Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySecret) {
		this.restTemplate = builder.build();
		this.walletServiceUrl = walletServiceUrl;
		this.gatewaySecret = gatewaySecret;
	}

	public void reserveFunds(Long userId, BigDecimal amount, String referenceType, String referenceId, String description) {
		String url = walletServiceUrl + "/api/v1/internal/wallet/reservations";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Gateway-Secret", gatewaySecret);

		Map<String, Object> payload = Map.of(
				"userId", userId,
				"amount", amount,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description
		);

		try {
			HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
			ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
			log.info("Reserved funds user={} amount={} ref={}: status={}", userId, amount, referenceId, response.getStatusCode());
		} catch (HttpStatusCodeException ex) {
			log.error("Failed to reserve funds for user={}: status={} body={}", userId, ex.getStatusCode(), ex.getResponseBodyAsString());
			if (ex.getStatusCode() == HttpStatus.BAD_REQUEST || ex.getStatusCode() == HttpStatus.CONFLICT) {
				throw new BadRequestException("Insufficient GeekCoins balance or reservation conflict");
			}
			throw new RuntimeException("Wallet service reservation error", ex);
		} catch (Exception ex) {
			log.error("Network error communicating with wallet service: {}", ex.getMessage());
			throw new RuntimeException("Could not contact wallet service", ex);
		}
	}

	public void releaseReservedFunds(Long userId, BigDecimal amount, String referenceType, String referenceId, String description) {
		String url = walletServiceUrl + "/api/v1/internal/wallet/reservations/release";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Gateway-Secret", gatewaySecret);

		Map<String, Object> payload = Map.of(
				"userId", userId,
				"amount", amount,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description
		);

		try {
			HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
			ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
			log.info("Released reserved funds user={} amount={} ref={}: status={}", userId, amount, referenceId, response.getStatusCode());
		} catch (Exception ex) {
			log.error("Failed to release reserved funds for user={}: {}", userId, ex.getMessage());
		}
	}

	public void settleSessionPayment(
			Long studentUserId,
			Long instructorUserId,
			BigDecimal reservedAmount,
			BigDecimal chargeAmount,
			String referenceType,
			String referenceId,
			String description) {
		String url = walletServiceUrl + "/api/v1/internal/wallet/settlements";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Gateway-Secret", gatewaySecret);

		Map<String, Object> payload = Map.of(
				"studentUserId", studentUserId,
				"instructorUserId", instructorUserId,
				"reservedAmount", reservedAmount,
				"chargeAmount", chargeAmount,
				"referenceType", referenceType,
				"referenceId", referenceId,
				"description", description
		);

		try {
			HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
			ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
			log.info("Settled session payment student={} instructor={} charge={} ref={}: status={}",
					studentUserId, instructorUserId, chargeAmount, referenceId, response.getStatusCode());
		} catch (Exception ex) {
			log.error("Failed to settle session payment ref={}: {}", referenceId, ex.getMessage());
		}
	}
}

