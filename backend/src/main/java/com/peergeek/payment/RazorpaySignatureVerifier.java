package com.peergeek.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class RazorpaySignatureVerifier {

	private static final String HMAC_SHA256 = "HmacSHA256";

	/**
	 * Verifies standard checkout payment signature: HMAC_SHA256(orderId + "|" + paymentId, secret)
	 */
	public boolean verifyPaymentSignature(String orderId, String paymentId, String signature, String secret) {
		if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature) || isBlank(secret)) {
			return false;
		}
		String payload = orderId + "|" + paymentId;
		String expectedSignature = calculateHmacSha256(payload, secret);
		return constantTimeEquals(expectedSignature, signature);
	}

	/**
	 * Verifies raw webhook signature: HMAC_SHA256(rawBody, webhookSecret)
	 */
	public boolean verifyWebhookSignature(String rawBody, String signature, String webhookSecret) {
		if (rawBody == null || isBlank(signature) || isBlank(webhookSecret)) {
			return false;
		}
		String expectedSignature = calculateHmacSha256(rawBody, webhookSecret);
		return constantTimeEquals(expectedSignature, signature);
	}

	public String calculateHmacSha256(String data, String secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
			mac.init(secretKeySpec);
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return bytesToHex(hash);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", ex);
		}
	}

	private boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) return false;
		byte[] aBytes = a.toLowerCase().getBytes(StandardCharsets.UTF_8);
		byte[] bBytes = b.toLowerCase().getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(aBytes, bBytes);
	}

	private String bytesToHex(byte[] bytes) {
		StringBuilder hexString = new StringBuilder(2 * bytes.length);
		for (byte b : bytes) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}

