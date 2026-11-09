package com.peergeek.auth;

import org.springframework.http.HttpStatus;
import com.peergeek.exception.ApiException;

public final class UnauthorizedAuthException extends ApiException {
	public UnauthorizedAuthException() {
		super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Email or password is incorrect");
	}
}
