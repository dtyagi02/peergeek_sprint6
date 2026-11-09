package com.peergeek.referral;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "peergeek.referral")
public class ReferralSettings {

	private BigDecimal inviterReward = new BigDecimal("100.00");
	private BigDecimal newUserReward = new BigDecimal("50.00");
	private int expiryDays = 7;
	private int maxInvitationsPerDay = 10;
	private String baseUrl = "http://localhost:5173";

	public BigDecimal getInviterReward() { return inviterReward; }
	public void setInviterReward(BigDecimal inviterReward) { this.inviterReward = inviterReward; }

	public BigDecimal getNewUserReward() { return newUserReward; }
	public void setNewUserReward(BigDecimal newUserReward) { this.newUserReward = newUserReward; }

	public int getExpiryDays() { return expiryDays; }
	public void setExpiryDays(int expiryDays) { this.expiryDays = expiryDays; }

	public int getMaxInvitationsPerDay() { return maxInvitationsPerDay; }
	public void setMaxInvitationsPerDay(int maxInvitationsPerDay) { this.maxInvitationsPerDay = maxInvitationsPerDay; }

	public String getBaseUrl() { return baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}

