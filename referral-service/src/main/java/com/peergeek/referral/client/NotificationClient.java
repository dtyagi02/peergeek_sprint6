package com.peergeek.referral.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class NotificationClient {

	private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

	private final RestTemplate restTemplate;
	private final String notificationServiceUrl;
	private final String gatewaySecret;

	public NotificationClient(
			@Value("${peergeek.services.notification-url:http://peergeek-notification-test:8087}") String notificationServiceUrl,
			@Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySecret) {
		this.restTemplate = new RestTemplate();
		this.notificationServiceUrl = notificationServiceUrl;
		this.gatewaySecret = gatewaySecret;
	}

	public void createNotification(
			Long userId,
			String type,
			String title,
			String message,
			String resourceType,
			Long resourceId,
			String actionUrl,
			String eventKey) {

		String url = notificationServiceUrl + "/api/v1/notifications/internal";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Gateway-Secret", gatewaySecret);

		Map<String, Object> body = Map.of(
				"userId", userId,
				"type", type,
				"title", title,
				"message", message,
				"resourceType", resourceType != null ? resourceType : "",
				"resourceId", resourceId != null ? resourceId : 0L,
				"actionUrl", actionUrl != null ? actionUrl : "",
				"eventKey", eventKey != null ? eventKey : ""
		);

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

		try {
			restTemplate.postForEntity(url, entity, Map.class);
			log.info("Notification sent for userId={} type={}", userId, type);
		} catch (Exception ex) {
			log.warn("Failed to create in-app notification: {}", ex.getMessage());
		}
	}

	public void sendReferralInvitationEmail(
			String recipientEmail,
			String inviterName,
			String referralCode,
			String registrationUrl,
			String inviterCoins,
			String newUserCoins,
			int expiryDays) {

		String url = notificationServiceUrl + "/api/v1/notifications/internal/email";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Gateway-Secret", gatewaySecret);

		String subject = inviterName + " invited you to join PeerGeek!";
		String bodyText = String.format(
				"Hi,\n\n%s has invited you to join PeerGeek!\nUse referral code %s during registration or click here: %s\n" +
						"You will receive %s GeekCoins, and %s will receive %s GeekCoins!\nThis invite expires in %d days.",
				inviterName, referralCode, registrationUrl, newUserCoins, inviterName, inviterCoins, expiryDays);

		Map<String, Object> body = Map.of(
				"to", recipientEmail,
				"subject", subject,
				"body", bodyText,
				"metadata", Map.of(
						"template", "REFERRAL_INVITATION",
						"referralCode", referralCode,
						"inviterName", inviterName
				)
		);

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

		try {
			restTemplate.postForEntity(url, entity, Map.class);
			log.info("Referral invitation email dispatched to notification service for {}", recipientEmail);
		} catch (Exception ex) {
			log.warn("Failed to dispatch invitation email to notification service: {}", ex.getMessage());
		}
	}
}

