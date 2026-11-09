package com.peergeek.referral;

import java.math.BigDecimal;
import com.peergeek.auth.MailDeliveryException;
import com.peergeek.auth.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ReferralEmailDispatcher {
	private static final Logger log = LoggerFactory.getLogger(ReferralEmailDispatcher.class);
	private final MailSender mailSender;

	public ReferralEmailDispatcher(MailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Async("passwordResetEmailExecutor")
	public void dispatch(
			String recipient,
			String inviterName,
			String referralCode,
			String registrationUrl,
			BigDecimal inviterReward,
			BigDecimal newUserReward,
			int expiryDays,
			String requestId) {
		try {
			mailSender.sendReferralInvitation(
					recipient, inviterName, referralCode, registrationUrl, inviterReward, newUserReward, expiryDays);
		} catch (MailDeliveryException exception) {
			log.error("Referral invitation email delivery failed recipient={} requestId={}", recipient, requestId);
		}
	}
}
