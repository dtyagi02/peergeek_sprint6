package com.peergeek.assignment;

import java.time.Clock;
import jakarta.servlet.http.HttpServletRequest;
import com.peergeek.exception.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {"com.peergeek.assignment", "com.peergeek.submission"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssignmentApiExceptionAdvice {

	private final Clock clock;

	public AssignmentApiExceptionAdvice(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(AssignmentValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			AssignmentValidationException exception, HttpServletRequest request) {
		ApiErrorResponse body = new ApiErrorResponse(
				clock.instant(),
				HttpStatus.BAD_REQUEST.value(),
				HttpStatus.BAD_REQUEST.getReasonPhrase(),
				exception.getCode(),
				exception.getMessage(),
				request.getRequestURI(),
				exception.getFieldErrors());
		return ResponseEntity.badRequest().body(body);
	}
}
