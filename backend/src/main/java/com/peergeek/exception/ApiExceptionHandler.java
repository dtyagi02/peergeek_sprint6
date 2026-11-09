package com.peergeek.exception;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
		return response(exception.getStatus(), exception.getCode(), exception.getMessage(), request, List.of());
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiErrorResponse> handleResponseStatus(
			ResponseStatusException exception, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
		String message = exception.getReason() == null || exception.getReason().isBlank()
				? status.getReasonPhrase()
				: exception.getReason();
		return response(status, status.name(), message, request, List.of());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiErrorResponse> handleAccessDenied(
			AccessDeniedException exception, HttpServletRequest request) {
		return response(HttpStatus.FORBIDDEN, "FORBIDDEN",
				"You do not have permission to access this resource", request, List.of());
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
	ResponseEntity<ApiErrorResponse> handleBinding(Exception exception, HttpServletRequest request) {
		BindException binding = (BindException) exception;
		List<FieldErrorResponse> fields = binding.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorResponse(error.getField(), safeMessage(error.getDefaultMessage())))
				.sorted(Comparator.comparing(FieldErrorResponse::field))
				.toList();
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fields);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception, HttpServletRequest request) {
		List<FieldErrorResponse> fields = exception.getConstraintViolations().stream()
				.map(error -> new FieldErrorResponse(error.getPropertyPath().toString(), safeMessage(error.getMessage())))
				.sorted(Comparator.comparing(FieldErrorResponse::field))
				.toList();
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fields);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleUnreadable(
			HttpMessageNotReadableException exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is missing or malformed", request, List.of());
	}

	@ExceptionHandler({MethodArgumentTypeMismatchException.class, TypeMismatchException.class})
	ResponseEntity<ApiErrorResponse> handleTypeMismatch(Exception exception, HttpServletRequest request) {
		String field = exception instanceof MethodArgumentTypeMismatchException mismatch
				? mismatch.getName()
				: "request";
		return response(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", "A request value has an invalid type", request,
				List.of(new FieldErrorResponse(field, "must be a valid value")));
	}

	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", request, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
		return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
				"The request method is not supported for this resource", request, List.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unhandled request failure requestId={}", MDC.get(RequestIdFilter.MDC_KEY), exception);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				"An unexpected error occurred", request, List.of());
	}

	private ResponseEntity<ApiErrorResponse> response(
			HttpStatus status, String code, String message, HttpServletRequest request, List<FieldErrorResponse> fields) {
		ApiErrorResponse body = new ApiErrorResponse(
				Instant.now(), status.value(), status.getReasonPhrase(), code, message, request.getRequestURI(), fields);
		return ResponseEntity.status(status).body(body);
	}

	private String safeMessage(String message) {
		return message == null || message.isBlank() ? "is invalid" : message;
	}
}
