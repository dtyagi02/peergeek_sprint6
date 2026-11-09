package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.util.List;
import com.peergeek.assignment.QuestionOption;
import com.peergeek.assignment.QuestionType;

public record QuestionDto(
		Long id,
		int position,
		QuestionType type,
		String prompt,
		BigDecimal points,
		List<QuestionOption> options,
		String codeLanguage) {
}
