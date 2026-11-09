package com.peergeek.identity.exception;

import java.util.List;

public class InputValidationException extends RuntimeException {
	private final List<FieldErrorResponse> errors;

	public InputValidationException(String message, List<FieldErrorResponse> errors) {
		super(message);
		this.errors = errors != null ? List.copyOf(errors) : List.of();
	}

	public List<FieldErrorResponse> getErrors() {
		return errors;
	}
}

