package com.peergeek.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("peergeek.auth")
public class AuthSettings {
	public static final String DEVELOPMENT_SECRET =
			"development-only-peergeek-jwt-secret-replace-before-production";

	private String jwtSecret = DEVELOPMENT_SECRET;
	private long accessTokenTtlSeconds = 900;
	private long refreshTokenTtlDays = 14;
	private long resetOtpTtlMinutes = 10;
	private long resetOtpCooldownSeconds = 60;
	private boolean refreshCookieSecure = false;

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtlSeconds;
	}

	public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public long getRefreshTokenTtlDays() {
		return refreshTokenTtlDays;
	}

	public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
		this.refreshTokenTtlDays = refreshTokenTtlDays;
	}

	public long getResetOtpTtlMinutes() {
		return resetOtpTtlMinutes;
	}

	public void setResetOtpTtlMinutes(long resetOtpTtlMinutes) {
		this.resetOtpTtlMinutes = resetOtpTtlMinutes;
	}

	public long getResetOtpCooldownSeconds() {
		return resetOtpCooldownSeconds;
	}

	public void setResetOtpCooldownSeconds(long resetOtpCooldownSeconds) {
		this.resetOtpCooldownSeconds = resetOtpCooldownSeconds;
	}

	public boolean isRefreshCookieSecure() {
		return refreshCookieSecure;
	}

	public void setRefreshCookieSecure(boolean refreshCookieSecure) {
		this.refreshCookieSecure = refreshCookieSecure;
	}
}

