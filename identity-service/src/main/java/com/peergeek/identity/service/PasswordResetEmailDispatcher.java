package com.peergeek.identity.service;

import com.peergeek.identity.exception.MailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailDispatcher {
	private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailDispatcher.class);
	private final MailSender mailSender;

	public PasswordResetEmailDispatcher(MailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Async
	public void dispatch(String recipient, String otp, long expiryMinutes, String requestId) {
		try {
			mailSender.sendPasswordReset(recipient, otp, expiryMinutes);
		} catch (MailDeliveryException exception) {
			log.error("Password reset email delivery failed requestId={}", requestId, exception);
		}
	}

	@Async
	public void dispatchConfirmation(String recipient, String requestId) {
		try {
			mailSender.sendPasswordResetConfirmation(recipient);
		} catch (MailDeliveryException exception) {
			log.error("Password reset confirmation email delivery failed requestId={}", requestId, exception);
		}
	}
}

