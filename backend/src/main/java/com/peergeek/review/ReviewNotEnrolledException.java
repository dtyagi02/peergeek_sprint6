package com.peergeek.review;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReviewNotEnrolledException extends ApiException {
	public ReviewNotEnrolledException() {
		super(HttpStatus.FORBIDDEN, "REVIEW_ENROLLMENT_REQUIRED", "Only enrolled learners can review this course");
	}
}
