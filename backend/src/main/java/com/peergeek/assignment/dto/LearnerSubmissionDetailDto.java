package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.peergeek.assignment.AssignmentKind;
import com.peergeek.assignment.QuestionType;

public record LearnerSubmissionDetailDto(
		long submissionId,
		long assignmentId,
		String assignmentTitle,
		AssignmentKind kind,
		String status,
		Instant startedAt,
		Instant deadlineAt,
		Instant submittedAt,
		boolean late,
		boolean autoSubmitted,
		boolean autoGraded,
		BigDecimal marks,
		BigDecimal totalPoints,
		BigDecimal maxPoints,
		String overallFeedback,
		String textAnswer,
		List<SubmissionFileInfoDto> files,
		List<LearnerSubmissionQuestionDto> questions
) {
	public LearnerSubmissionDetailDto {
		files = files == null ? List.of() : List.copyOf(files);
		questions = questions == null ? List.of() : List.copyOf(questions);
	}

	public record SubmissionFileInfoDto(
			long id,
			String filename,
			String mimeType,
			long sizeBytes,
			String status
	) {}

	public record LearnerSubmissionQuestionDto(
			long questionId,
			int position,
			QuestionType type,
			String prompt,
			BigDecimal maxPoints,
			String codeLanguage,
			List<SubmissionQuestionOptionDto> options,
			String answerText,
			List<String> selectedOptionIds,
			BigDecimal pointsAwarded,
			String feedback,
			List<String> correctOptionIds
	) {
		public LearnerSubmissionQuestionDto {
			options = options == null ? List.of() : List.copyOf(options);
			selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
			correctOptionIds = correctOptionIds == null ? null : List.copyOf(correctOptionIds);
		}

		@JsonProperty("id")
		public long id() {
			return questionId;
		}

		@JsonProperty("points")
		public BigDecimal points() {
			return maxPoints;
		}
	}

	public record SubmissionQuestionOptionDto(
			String id,
			String text,
			Boolean isCorrect
	) {
		@JsonProperty("correct")
		public Boolean correct() {
			return isCorrect;
		}
	}
}

