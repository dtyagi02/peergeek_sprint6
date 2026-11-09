package com.peergeek.identity.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InputValidationException.class)
	public ResponseEntity<Map<String, Object>> handleInputValidation(InputValidationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"status", HttpStatus.BAD_REQUEST.value(),
				"message", ex.getMessage(),
				"errors", ex.getErrors(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
				.collect(Collectors.toList());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"status", HttpStatus.BAD_REQUEST.value(),
				"message", "Validation failed",
				"errors", errors,
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"status", HttpStatus.CONFLICT.value(),
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
				"status", HttpStatus.NOT_FOUND.value(),
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"status", HttpStatus.BAD_REQUEST.value(),
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(UnauthorizedAuthException.class)
	public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedAuthException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
				"status", HttpStatus.UNAUTHORIZED.value(),
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(EmailNotVerifiedException.class)
	public ResponseEntity<Map<String, Object>> handleEmailNotVerified(EmailNotVerifiedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
				"status", HttpStatus.FORBIDDEN.value(),
				"message", ex.getMessage(),
				"email", ex.getEmail(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
				"status", HttpStatus.FORBIDDEN.value(),
				"message", "Access is denied",
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
				"status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
				"timestamp", Instant.now().toString()));
	}
}

