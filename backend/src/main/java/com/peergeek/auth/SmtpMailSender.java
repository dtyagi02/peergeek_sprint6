package com.peergeek.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailSender implements MailSender {
	private final JavaMailSender mailSender;
	private final org.springframework.core.env.Environment environment;

	public SmtpMailSender(
			ObjectProvider<JavaMailSender> mailSenders, org.springframework.core.env.Environment environment) {
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
			String from = environment.getProperty("PEERGEEK_MAIL_FROM", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Password reset email could not be sent");
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
					<p>It expires in %d minutes. Enter this code to activate your account.</p>
					</body></html>
					""".formatted(otp, expiryMinutes));
			String from = environment.getProperty("PEERGEEK_MAIL_FROM", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Registration verification email could not be sent");
		}
	}

	@Override
	public void sendPasswordResetConfirmation(String recipient) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(recipient);
			helper.setSubject("Your PeerGeek password has been changed");
			helper.setText("Your PeerGeek password was successfully changed. If you did not make this change, please contact support immediately.", """
					<!doctype html><html><body><p>Hello,</p>
					<p>Your PeerGeek password was successfully updated.</p>
					<p>If you did not make this change, please reset your password or contact support immediately.</p>
					</body></html>
					""");
			String from = environment.getProperty("PEERGEEK_MAIL_FROM", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Password change confirmation email could not be sent");
		}
	}

	@Override
	public void sendReferralInvitation(
			String recipient,
			String inviterName,
			String referralCode,
			String registrationUrl,
			java.math.BigDecimal inviterReward,
			java.math.BigDecimal newUserReward,
			int expiryDays) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(recipient);
			String displayName = (inviterName != null && !inviterName.isBlank()) ? inviterName : "A friend";
			helper.setSubject(displayName + " invited you to join PeerGeek!");
			String plainText = String.format(
					"Hi!\n\n%s has invited you to join PeerGeek, the peer-to-peer learning platform.\n\n"
							+ "Referral Code: %s\n"
							+ "Register here: %s\n\n"
							+ "When you sign up and verify your email within %d days, you will receive %s GeekCoins, and %s will receive %s GeekCoins!\n\n"
							+ "Best regards,\nThe PeerGeek Team",
					displayName, referralCode, registrationUrl,
					expiryDays,
					newUserReward.stripTrailingZeros().toPlainString(),
					displayName, inviterReward.stripTrailingZeros().toPlainString());

			String htmlText = String.format("""
					<!doctype html>
					<html>
					<head>
					  <meta charset="utf-8">
					  <style>
					    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #1e293b; background-color: #f8fafc; padding: 20px; }
					    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px; border: 1px solid #e2e8f0; }
					    .header { font-size: 24px; font-weight: 800; color: #0f172a; margin-bottom: 16px; }
					    .code-box { background: #f1f5f9; border: 2px dashed #94a3b8; border-radius: 8px; padding: 16px; text-align: center; margin: 24px 0; }
					    .code { font-size: 28px; font-weight: 700; letter-spacing: 4px; color: #d97706; }
					    .btn { display: inline-block; background-color: #d97706; color: #ffffff !important; text-decoration: none; padding: 12px 28px; border-radius: 8px; font-weight: 600; margin: 20px 0; }
					    .reward-pill { background: #fef3c7; color: #92400e; padding: 6px 12px; border-radius: 20px; font-weight: 600; display: inline-block; margin-top: 8px; }
					    .footer { margin-top: 32px; font-size: 13px; color: #64748b; border-top: 1px solid #e2e8f0; padding-top: 16px; }
					  </style>
					</head>
					<body>
					  <div class="container">
					    <div class="header">Join PeerGeek & Earn GeekCoins! 🪙</div>
					    <p>Hello,</p>
					    <p><strong>%s</strong> has invited you to join <strong>PeerGeek</strong>, the interactive learning community where students and instructors collaborate, clear doubts, and master technical skills.</p>
					    <div class="reward-pill">✨ You will earn %s GeekCoins upon joining!</div>
					    <div class="code-box">
					      <div style="font-size: 14px; color: #64748b; margin-bottom: 8px;">Your Unique Referral Code</div>
					      <div class="code">%s</div>
					    </div>
					    <p style="text-align: center;">
					      <a href="%s" class="btn">Claim Your GeekCoins & Register</a>
					    </p>
					    <p style="font-size: 14px; color: #64748b;">Or copy this link to your browser:<br><a href="%s" style="color: #d97706; word-break: break-all;">%s</a></p>
					    <div class="footer">
					      <p>This invitation expires in %d days. If you did not expect this invitation, you can safely ignore this email.</p>
					      <p>&copy; PeerGeek. All rights reserved.</p>
					    </div>
					  </div>
					</body>
					</html>
					""",
					displayName,
					newUserReward.stripTrailingZeros().toPlainString(),
					referralCode,
					registrationUrl,
					registrationUrl, registrationUrl, expiryDays);

			helper.setText(plainText, htmlText);
			String from = environment.getProperty("PEERGEEK_MAIL_FROM", "no-reply@peergeek.local");
			helper.setFrom(from);
			mailSender.send(message);
		} catch (MessagingException | MailException exception) {
			throw new MailDeliveryException("Referral invitation email could not be sent");
		}
	}
}
