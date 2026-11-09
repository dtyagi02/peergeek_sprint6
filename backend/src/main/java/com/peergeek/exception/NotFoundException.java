package com.peergeek.exception;

import org.springframework.http.HttpStatus;

public final class NotFoundException extends ApiException {
	public NotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
	}
}
