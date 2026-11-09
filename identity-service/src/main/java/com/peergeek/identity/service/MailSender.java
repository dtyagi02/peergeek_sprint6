package com.peergeek.identity.service;

public interface MailSender {
	void sendRegistrationVerification(String recipient, String otp, long expiryMinutes);
	void sendPasswordReset(String recipient, String otp, long expiryMinutes);
	void sendPasswordResetConfirmation(String recipient);
}

