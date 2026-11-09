package com.peergeek.identity.service;

import com.peergeek.identity.exception.MailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailSender implements MailSender {
	private final JavaMailSender mailSender;
	private final Environment environment;

	public SmtpMailSender(
			ObjectProvider<JavaMailSender> mailSenders, Environment environment) {
		this.mailSender = mailSenders.getIfAvailable(() -> {
			JavaMailSenderImpl fallback = new JavaMailSenderImpl();
			fallback.setHost(environment.getProperty("spring.mail.host", "localhost"));
			fallback.setPort(environment.getProperty("spring.mail.port", Integer.class, 1025));
			return fallback;
		});
		this.environment = environment;
	}

	@Override
	public void sendPasswordReset(String recipient, String otp, long expiryMinutes) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(recipient);
			helper.setSubject("PeerGeek password reset");
			helper.setText("Your PeerGeek password reset code is " + otp
					+ ". It expires in " + expiryMinutes + " minutes.", """
					<!doctype html><html><body><p>Your PeerGeek password reset code is</p>
					<p style="font-size:24px;font-weight:bold;letter-spacing:4px">%s</p>
					<p>It expires in %d minutes. If you did not request this, ignore this email.</p>
					</body></html>
					""".formatted(otp, expiryMinutes));
			String from = environment.getProperty("peergeek.mail.from", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Password reset email could not be sent", exception);
		}
	}

	@Override
	public void sendPasswordResetConfirmation(String recipient) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(recipient);
			helper.setSubject("PeerGeek password changed");
			helper.setText("Your PeerGeek password has been successfully updated.", """
					<!doctype html><html><body><p>Your PeerGeek password has been successfully updated.</p>
					</body></html>
					""");
			String from = environment.getProperty("peergeek.mail.from", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Password reset confirmation email could not be sent", exception);
		}
	}

	@Override
	public void sendRegistrationVerification(String recipient, String otp, long expiryMinutes) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(recipient);
			helper.setSubject("Verify your email - PeerGeek");
			helper.setText("Welcome to PeerGeek! Your verification code is " + otp
					+ ". It expires in " + expiryMinutes + " minutes.", """
					<!doctype html><html><body><p>Welcome to PeerGeek! Your email verification code is</p>
					<p style="font-size:24px;font-weight:bold;letter-spacing:4px">%s</p>
					<p>It expires in %d minutes.</p>
					</body></html>
					""".formatted(otp, expiryMinutes));
			String from = environment.getProperty("peergeek.mail.from", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Registration verification email could not be sent", exception);
		}
	}
}

