package com.peergeek.course;

import java.util.List;
import com.peergeek.exception.ApiException;
import com.peergeek.exception.FieldErrorResponse;
import org.springframework.http.HttpStatus;

public final class CourseInputValidationException extends ApiException {
	private final List<FieldErrorResponse> fieldErrors;

	public CourseInputValidationException(String field, String message) {
		this(List.of(new FieldErrorResponse(field, message)));
	}

	public CourseInputValidationException(List<FieldErrorResponse> fieldErrors) {
		super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public List<FieldErrorResponse> getFieldErrors() { return fieldErrors; }
}
