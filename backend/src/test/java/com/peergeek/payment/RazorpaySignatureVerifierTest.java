package com.peergeek.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpaySignatureVerifierTest {

	private RazorpaySignatureVerifier verifier;
	private final String secret = "test_razorpay_secret_12345";

	@BeforeEach
	void setUp() {
		verifier = new RazorpaySignatureVerifier();
	}

	private String calculateHmacSha256(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	@DisplayName("Valid payment checkout signature verification succeeds")
	void verifyPaymentSignature_valid() {
		String orderId = "order_Oq7Jk8Lm9Np0";
		String paymentId = "pay_Pq1234567890";
		String payload = orderId + "|" + paymentId;
		String validSignature = calculateHmacSha256(payload, secret);

		boolean isValid = verifier.verifyPaymentSignature(orderId, paymentId, validSignature, secret);
		assertThat(isValid).isTrue();
	}

	@Test
	@DisplayName("Tampered payment signature fails verification")
	void verifyPaymentSignature_tamperedSignature() {
		String orderId = "order_Oq7Jk8Lm9Np0";
		String paymentId = "pay_Pq1234567890";
		String fakeSignature = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

		boolean isValid = verifier.verifyPaymentSignature(orderId, paymentId, fakeSignature, secret);
		assertThat(isValid).isFalse();
	}

	@Test
	@DisplayName("Mismatched order ID or payment ID fails verification")
	void verifyPaymentSignature_tamperedPayload() {
		String orderId = "order_Oq7Jk8Lm9Np0";
		String paymentId = "pay_Pq1234567890";
		String validSignature = calculateHmacSha256(orderId + "|" + paymentId, secret);

		// Different payment id
		boolean isValid = verifier.verifyPaymentSignature(orderId, "pay_DIFFERENT", validSignature, secret);
		assertThat(isValid).isFalse();
	}

	@Test
	@DisplayName("Null or blank parameters return false gracefully")
	void verifyPaymentSignature_nullHandling() {
		assertThat(verifier.verifyPaymentSignature(null, "pay_1", "sig", secret)).isFalse();
		assertThat(verifier.verifyPaymentSignature("order_1", null, "sig", secret)).isFalse();
		assertThat(verifier.verifyPaymentSignature("order_1", "pay_1", null, secret)).isFalse();
		assertThat(verifier.verifyPaymentSignature("order_1", "pay_1", "", secret)).isFalse();
		assertThat(verifier.verifyPaymentSignature("order_1", "pay_1", "sig", null)).isFalse();
	}

	@Test
	@DisplayName("Valid webhook payload signature succeeds")
	void verifyWebhookSignature_valid() {
		String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
		String validSignature = calculateHmacSha256(payload, secret);

		boolean isValid = verifier.verifyWebhookSignature(payload, validSignature, secret);
		assertThat(isValid).isTrue();
	}

	@Test
	@DisplayName("Tampered webhook payload fails signature verification")
	void verifyWebhookSignature_tampered() {
		String payload = "{\"event\":\"payment.captured\",\"amount\":10000}";
		String validSignature = calculateHmacSha256(payload, secret);

		String tamperedPayload = "{\"event\":\"payment.captured\",\"amount\":99999}";
		boolean isValid = verifier.verifyWebhookSignature(tamperedPayload, validSignature, secret);
		assertThat(isValid).isFalse();
	}
}

