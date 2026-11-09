package com.peergeek.discovery;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DiscoveryValidationException extends ApiException {
	public DiscoveryValidationException(String message) {
		super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}
}
