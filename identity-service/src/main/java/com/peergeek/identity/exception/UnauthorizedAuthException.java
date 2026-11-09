package com.peergeek.identity.exception;

public class UnauthorizedAuthException extends RuntimeException {
	public UnauthorizedAuthException() {
		super("Invalid or expired session. Please sign in again.");
	}

	public UnauthorizedAuthException(String message) {
		super(message);
	}
}

