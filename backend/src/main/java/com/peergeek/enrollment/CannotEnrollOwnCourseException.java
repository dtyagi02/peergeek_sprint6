package com.peergeek.enrollment;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CannotEnrollOwnCourseException extends ApiException {
	public CannotEnrollOwnCourseException() {
		super(HttpStatus.CONFLICT, "CANNOT_ENROLL_OWN_COURSE", "Instructors cannot enroll in their own course");
	}
}
