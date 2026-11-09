package com.peergeek.courselearning.exception;

public class EnrollmentRequiredException extends RuntimeException {
    public EnrollmentRequiredException() {
        super("Enrollment is required to access course content");
    }
}

