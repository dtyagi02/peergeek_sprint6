package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.util.List;
import com.peergeek.assignment.QuestionType;

public record LearnerQuestionDto(
		long id,
		int position,
		QuestionType type,
		String prompt,
		BigDecimal points,
		List<LearnerQuestionOptionDto> options,
		String codeLanguage
) {
	public LearnerQuestionDto {
		options = options == null ? List.of() : List.copyOf(options);
	}
}
