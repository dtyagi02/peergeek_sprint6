package com.peergeek.courselearning.exception;

public class CannotEnrollOwnCourseException extends RuntimeException {
    public CannotEnrollOwnCourseException() {
        super("Instructors cannot enroll in their own course");
    }
}

