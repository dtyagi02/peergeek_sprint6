package com.peergeek.courselearning.review;

import com.peergeek.courselearning.exception.BadRequestException;

public class CannotReviewOwnCourseException extends BadRequestException {
    public CannotReviewOwnCourseException() {
        super("You cannot review your own course.");
    }
}
