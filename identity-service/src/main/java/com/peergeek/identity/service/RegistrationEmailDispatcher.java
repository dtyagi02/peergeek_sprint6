package com.peergeek.identity.service;

import com.peergeek.identity.exception.MailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RegistrationEmailDispatcher {
	private static final Logger log = LoggerFactory.getLogger(RegistrationEmailDispatcher.class);
	private final MailSender mailSender;

	public RegistrationEmailDispatcher(MailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Async
	public void dispatch(String recipient, String otp, long expiryMinutes, String requestId) {
		try {
			mailSender.sendRegistrationVerification(recipient, otp, expiryMinutes);
		} catch (MailDeliveryException exception) {
			log.error("Registration verification email delivery failed requestId={}", requestId, exception);
		}
	}
}

