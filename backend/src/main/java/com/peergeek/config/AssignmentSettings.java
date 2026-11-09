package com.peergeek.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("peergeek.assignment")
public class AssignmentSettings {

	private int maxAssignmentsPerCourse = 100;
	private int maxTitleLength = 255;
	private int maxInstructionsLength = 10_000;
	private int maxQuestionPromptLength = 5_000;
	private int maxOptionTextLength = 500;
	private int maxDescriptiveAnswerLength = 20_000;
	private int maxCodeAnswerLength = 50_000;
	private int maxProjectTextAnswerLength = 20_000;
	private int maxFeedbackLength = 5_000;
	private int graceSeconds = 10;
	private int maxFilesPerSubmission = 5;
	private long maxFileSizeBytes = 25L * 1024L * 1024L;
	private List<String> allowedFileExtensions = new ArrayList<>(List.of(
			"pdf", "docx", "pptx", "xlsx", "txt", "csv", "png", "jpeg", "jpg", "zip"
	));
	private int abandonedPendingHours = 24;
	private int failedCleanupHours = 24;

	public int getMaxAssignmentsPerCourse() {
		return maxAssignmentsPerCourse;
	}

	public void setMaxAssignmentsPerCourse(int maxAssignmentsPerCourse) {
		this.maxAssignmentsPerCourse = maxAssignmentsPerCourse;
	}

	public int getMaxTitleLength() {
		return maxTitleLength;
	}

	public void setMaxTitleLength(int maxTitleLength) {
		this.maxTitleLength = maxTitleLength;
	}

	public int getMaxInstructionsLength() {
		return maxInstructionsLength;
	}

	public void setMaxInstructionsLength(int maxInstructionsLength) {
		this.maxInstructionsLength = maxInstructionsLength;
	}

	public int getMaxQuestionPromptLength() {
		return maxQuestionPromptLength;
	}

	public void setMaxQuestionPromptLength(int maxQuestionPromptLength) {
		this.maxQuestionPromptLength = maxQuestionPromptLength;
	}

	public int getMaxOptionTextLength() {
		return maxOptionTextLength;
	}

	public void setMaxOptionTextLength(int maxOptionTextLength) {
		this.maxOptionTextLength = maxOptionTextLength;
	}

	public int getMaxDescriptiveAnswerLength() {
		return maxDescriptiveAnswerLength;
	}

	public void setMaxDescriptiveAnswerLength(int maxDescriptiveAnswerLength) {
		this.maxDescriptiveAnswerLength = maxDescriptiveAnswerLength;
	}

	public int getMaxCodeAnswerLength() {
		return maxCodeAnswerLength;
	}

	public void setMaxCodeAnswerLength(int maxCodeAnswerLength) {
		this.maxCodeAnswerLength = maxCodeAnswerLength;
	}

	public int getMaxProjectTextAnswerLength() {
		return maxProjectTextAnswerLength;
	}

	public void setMaxProjectTextAnswerLength(int maxProjectTextAnswerLength) {
		this.maxProjectTextAnswerLength = maxProjectTextAnswerLength;
	}

	public int getMaxFeedbackLength() {
		return maxFeedbackLength;
	}

	public void setMaxFeedbackLength(int maxFeedbackLength) {
		this.maxFeedbackLength = maxFeedbackLength;
	}

	public int getGraceSeconds() {
		return graceSeconds;
	}

	public void setGraceSeconds(int graceSeconds) {
		this.graceSeconds = graceSeconds;
	}

	public int getMaxFilesPerSubmission() {
		return maxFilesPerSubmission;
	}

	public void setMaxFilesPerSubmission(int maxFilesPerSubmission) {
		this.maxFilesPerSubmission = maxFilesPerSubmission;
	}

	public long getMaxFileSizeBytes() {
		return maxFileSizeBytes;
	}

	public void setMaxFileSizeBytes(long maxFileSizeBytes) {
		this.maxFileSizeBytes = maxFileSizeBytes;
	}

	public List<String> getAllowedFileExtensions() {
		return allowedFileExtensions;
	}

	public void setAllowedFileExtensions(List<String> allowedFileExtensions) {
		this.allowedFileExtensions = allowedFileExtensions;
	}

	public int getAbandonedPendingHours() {
		return abandonedPendingHours;
	}

	public void setAbandonedPendingHours(int abandonedPendingHours) {
		this.abandonedPendingHours = abandonedPendingHours;
	}

	public int getFailedCleanupHours() {
		return failedCleanupHours;
	}

	public void setFailedCleanupHours(int failedCleanupHours) {
		this.failedCleanupHours = failedCleanupHours;
	}
}
