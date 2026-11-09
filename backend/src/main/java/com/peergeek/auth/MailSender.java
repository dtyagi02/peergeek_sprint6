package com.peergeek.auth;

public interface MailSender {
	void sendPasswordReset(String recipient, String otp, long expiryMinutes);

	void sendRegistrationVerification(String recipient, String otp, long expiryMinutes);

	void sendPasswordResetConfirmation(String recipient);

	void sendReferralInvitation(
			String recipient,
			String inviterName,
			String referralCode,
			String registrationUrl,
			java.math.BigDecimal inviterReward,
			java.math.BigDecimal newUserReward,
			int expiryDays);
}
