package com.peergeek.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.exception.ApiErrorResponse;
import com.peergeek.exception.RequestIdFilter;
import com.peergeek.exception.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {
	private final ObjectMapper objectMapper;

	public SecurityErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(
			HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String code, String message) throws IOException {
		String requestId = MDC.get(RequestIdFilter.MDC_KEY);
		if (requestId != null) response.setHeader(RequestIdFilter.HEADER, requestId);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		ApiErrorResponse body = new ApiErrorResponse(
				Instant.now(), status.value(), status.getReasonPhrase(), code, message,
				request.getRequestURI(), List.<FieldErrorResponse>of());
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
