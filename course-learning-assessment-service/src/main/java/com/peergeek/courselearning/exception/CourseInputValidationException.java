package com.peergeek.courselearning.exception;

import java.util.List;

public class CourseInputValidationException extends RuntimeException {
    private final List<FieldErrorResponse> fieldErrors;

    public CourseInputValidationException(String field, String message) {
        this(List.of(new FieldErrorResponse(field, message)));
    }

    public CourseInputValidationException(List<FieldErrorResponse> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorResponse> getFieldErrors() {
        return fieldErrors;
    }
}

