package com.peergeek.auth;

import java.util.List;
import com.peergeek.exception.ApiException;
import com.peergeek.exception.FieldErrorResponse;
import org.springframework.http.HttpStatus;

public final class InputValidationException extends ApiException {
	private final List<FieldErrorResponse> fieldErrors;

	public InputValidationException(String message, List<FieldErrorResponse> fieldErrors) {
		super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public List<FieldErrorResponse> getFieldErrors() {
		return fieldErrors;
	}
}
