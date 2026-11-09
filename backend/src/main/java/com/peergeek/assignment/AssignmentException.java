package com.peergeek.assignment;

import com.peergeek.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AssignmentException extends ApiException {

	public AssignmentException(HttpStatus status, String code, String message) {
		super(status, code, message);
	}

	public static AssignmentException locked(String message) {
		return new AssignmentException(HttpStatus.CONFLICT, "ASSIGNMENT_LOCKED", message);
	}

	public static AssignmentException notOpen(String message) {
		return new AssignmentException(HttpStatus.CONFLICT, "ASSIGNMENT_NOT_OPEN", message);
	}

	public static AssignmentException deadlinePassed(String message) {
		return new AssignmentException(HttpStatus.CONFLICT, "DEADLINE_PASSED", message);
	}

	public static AssignmentException attemptExpired(String message) {
		return new AssignmentException(HttpStatus.CONFLICT, "ATTEMPT_EXPIRED", message);
	}

	public static AssignmentException alreadySubmitted(String message) {
		return new AssignmentException(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", message);
	}

	public static AssignmentException draftVersionConflict(String message) {
		return new AssignmentException(HttpStatus.CONFLICT, "DRAFT_VERSION_CONFLICT", message);
	}

	public static AssignmentException draftVersionConflict(long latestVersion) {
		return new AssignmentException(HttpStatus.CONFLICT, "DRAFT_VERSION_CONFLICT",
				"Draft version conflict. Current version is " + latestVersion);
	}

	public static AssignmentException gradingIncomplete(String message) {
		return new AssignmentException(HttpStatus.BAD_REQUEST, "GRADING_INCOMPLETE", message);
	}
}
