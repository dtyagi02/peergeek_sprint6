package com.peergeek.auth;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.peergeek.exception.ApiErrorResponse;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthValidationAdvice {
	@ExceptionHandler(InputValidationException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(
			InputValidationException exception, HttpServletRequest request) {
		ApiErrorResponse body = new ApiErrorResponse(
				Instant.now(), HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
				"VALIDATION_FAILED", exception.getMessage(), request.getRequestURI(), exception.getFieldErrors());
		return ResponseEntity.badRequest().body(body);
	}
}
