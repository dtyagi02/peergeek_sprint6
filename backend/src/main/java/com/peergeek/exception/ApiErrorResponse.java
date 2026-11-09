package com.peergeek.exception;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "Standard error response returned by the API.")
public record ApiErrorResponse(
		@Schema(format = "date-time") Instant timestamp,
		int status,
		String error,
		String code,
		String message,
		String path,
		List<FieldErrorResponse> fieldErrors) {
	public ApiErrorResponse {
		fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}
}
