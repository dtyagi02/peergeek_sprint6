package com.peergeek.courselearning.review;

import com.peergeek.courselearning.exception.ForbiddenException;

public class ReviewNotEnrolledException extends ForbiddenException {
    public ReviewNotEnrolledException() {
        super("You must be actively enrolled in this course to leave a review.");
    }
}
