package com.peergeek.audit;

public final class AuditAction {
	public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
	public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
	public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
	public static final String AUTH_PASSWORD_RESET_REQUESTED = "AUTH_PASSWORD_RESET_REQUESTED";
	public static final String AUTH_PASSWORD_RESET_COMPLETED = "AUTH_PASSWORD_RESET_COMPLETED";
	public static final String USER_REGISTERED = "USER_REGISTERED";
	public static final String USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
	public static final String AUTH_BOOTSTRAP_ADMIN_CREATED = "AUTH_BOOTSTRAP_ADMIN_CREATED";
	public static final String AUTH_EMAIL_VERIFIED = "AUTH_EMAIL_VERIFIED";
	public static final String AUTH_REGISTRATION_OTP_REQUESTED = "AUTH_REGISTRATION_OTP_REQUESTED";
	public static final String AUTH_RESET_OTP_VERIFIED = "AUTH_RESET_OTP_VERIFIED";
	public static final String REPORT_CREATED = "REPORT_CREATED";
	public static final String REPORT_STATUS_UPDATED = "REPORT_STATUS_UPDATED";
	public static final String REVIEW_CREATED = "REVIEW_CREATED";
	public static final String USER_BLOCKED = "USER_BLOCKED";
	public static final String USER_UNBLOCKED = "USER_UNBLOCKED";
	public static final String COMMUNITY_MESSAGE_CREATED = "COMMUNITY_MESSAGE_CREATED";
	public static final String COMMUNITY_MESSAGE_DELETED = "COMMUNITY_MESSAGE_DELETED";
	public static final String COMMUNITY_USER_BLOCKED = "COMMUNITY_USER_BLOCKED";
	public static final String COMMUNITY_USER_UNBLOCKED = "COMMUNITY_USER_UNBLOCKED";

	private AuditAction() {
	}
}
