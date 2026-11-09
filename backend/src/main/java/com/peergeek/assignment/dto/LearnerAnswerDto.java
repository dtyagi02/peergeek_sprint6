package com.peergeek.assignment.dto;

import java.util.List;

public record LearnerAnswerDto(
		long questionId,
		String answerText,
		List<String> selectedOptionIds
) {
	public LearnerAnswerDto {
		selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
	}
}
