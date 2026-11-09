package com.peergeek.auth;

import org.springframework.http.HttpStatus;
import com.peergeek.exception.ApiException;

public final class EmailNotVerifiedException extends ApiException {
	public EmailNotVerifiedException() {
		super(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Please verify your email before logging in.");
	}
}
