package com.peergeek.review;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CannotReviewOwnCourseException extends ApiException {
	public CannotReviewOwnCourseException() {
		super(HttpStatus.CONFLICT, "CANNOT_REVIEW_OWN_COURSE", "Instructors cannot review their own courses");
	}
}
