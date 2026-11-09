package com.peergeek.doubtsession.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"status", HttpStatus.CONFLICT.value(),
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
				"status", HttpStatus.FORBIDDEN.value(),
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String errorMsg = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.findFirst()
				.orElse("Validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"status", HttpStatus.BAD_REQUEST.value(),
				"message", errorMsg,
				"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
				"status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
				"timestamp", Instant.now().toString()));
	}
}

