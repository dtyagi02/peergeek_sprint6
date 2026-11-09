package com.peergeek.course;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import com.peergeek.exception.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CourseApiExceptionAdvice {
	@ExceptionHandler(CourseInputValidationException.class)
	ResponseEntity<ApiErrorResponse> validation(
			CourseInputValidationException exception, HttpServletRequest request) {
		ApiErrorResponse body = new ApiErrorResponse(
				Instant.now(), HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
				exception.getCode(), exception.getMessage(), request.getRequestURI(), exception.getFieldErrors());
		return ResponseEntity.badRequest().body(body);
	}
}
