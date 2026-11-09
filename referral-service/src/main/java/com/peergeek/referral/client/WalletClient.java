package com.peergeek.referral.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class WalletClient {

	private static final Logger log = LoggerFactory.getLogger(WalletClient.class);

	private final RestTemplate restTemplate;
	private final String walletServiceUrl;
	private final String gatewaySecret;

	public WalletClient(
			@Value("${peergeek.services.wallet-url:http://peergeek-wallet-payment-test:8088}") String walletServiceUrl,
			@Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySecret) {
		this.restTemplate = new RestTemplate();
		this.walletServiceUrl = walletServiceUrl;
		this.gatewaySecret = gatewaySecret;
	}

	public void batchReferralReward(
			Long referralId,
			Long inviterUserId,
			BigDecimal inviterRewardAmount,
			Long newUserId,
			BigDecimal newUserRewardAmount,
			String inviterDescription,
			String newUserDescription) {

		String url = walletServiceUrl + "/api/v1/internal/wallet/rewards/referral";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Gateway-Secret", gatewaySecret);

		Map<String, Object> body = Map.of(
				"referralId", referralId,
				"inviterUserId", inviterUserId,
				"inviterRewardAmount", inviterRewardAmount,
				"inviterDescription", inviterDescription != null ? inviterDescription : "Referral reward",
				"newUserId", newUserId,
				"newUserRewardAmount", newUserRewardAmount,
				"newUserDescription", newUserDescription != null ? newUserDescription : "Welcome referral bonus"
		);

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

		try {
			restTemplate.postForEntity(url, entity, Map.class);
			log.info("Batch referral reward successfully dispatched to wallet service for referralId={}", referralId);
		} catch (Exception ex) {
			log.error("Failed to post batch referral reward for referralId={}: {}", referralId, ex.getMessage(), ex);
			throw new RuntimeException("Wallet batch referral reward failed: " + ex.getMessage(), ex);
		}
	}
}

