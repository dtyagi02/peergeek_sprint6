package com.peergeek.enrollment;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public class EnrollmentRequiredException extends ApiException {
	public EnrollmentRequiredException() {
		super(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "You must be enrolled to access course content");
	}
}
