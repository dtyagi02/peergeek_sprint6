package com.peergeek.submission.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.peergeek.assignment.AssignmentKind;
import com.peergeek.assignment.QuestionOption;
import com.peergeek.assignment.QuestionType;

public record InstructorSubmissionDetailDto(
		long submissionId,
		long assignmentId,
		long courseId,
		String assignmentTitle,
		AssignmentKind kind,
		String status,
		long version,
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
		Long gradedBy,
		Instant gradedAt,
		List<InstructorSubmissionFileDto> files,
		List<InstructorSubmissionQuestionDto> questions
) {
	public InstructorSubmissionDetailDto {
		files = files == null ? List.of() : List.copyOf(files);
		questions = questions == null ? List.of() : List.copyOf(questions);
	}

	public record InstructorSubmissionFileDto(
			long id,
			String originalFilename,
			String mimeType,
			long sizeBytes,
			String status
	) {}

	public record InstructorSubmissionQuestionDto(
			long questionId,
			int position,
			QuestionType type,
			String prompt,
			BigDecimal maxPoints,
			String codeLanguage,
			List<QuestionOption> options,
			String answerText,
			List<String> selectedOptionIds,
			BigDecimal autoPoints,
			BigDecimal points,
			BigDecimal effectivePoints,
			String feedback
	) {
		public InstructorSubmissionQuestionDto {
			options = options == null ? List.of() : List.copyOf(options);
			selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
		}
	}
}
