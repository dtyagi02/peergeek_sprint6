package com.peergeek.course;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class CourseStateConflictException extends ApiException {
	public CourseStateConflictException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}
}
