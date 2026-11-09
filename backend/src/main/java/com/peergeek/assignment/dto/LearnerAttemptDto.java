package com.peergeek.assignment.dto;

import java.time.Instant;
import java.util.List;

public record LearnerAttemptDto(
		long submissionId,
		long assignmentId,
		String status,
		Instant startedAt,
		Instant deadlineAt,
		Instant serverNow,
		long version,
		String textAnswer,
		List<LearnerQuestionDto> questions,
		List<LearnerAnswerDto> answers
) {
	public LearnerAttemptDto {
		questions = questions == null ? List.of() : List.copyOf(questions);
		answers = answers == null ? List.of() : List.copyOf(answers);
	}
}
