package com.peergeek.submission.dto;

import java.math.BigDecimal;
import java.util.List;

public record GradeSubmissionRequest(
		long version,
		BigDecimal marks,
		List<QuestionGradeItem> questionGrades,
		String overallFeedback
) {
	public GradeSubmissionRequest {
		questionGrades = questionGrades == null ? List.of() : List.copyOf(questionGrades);
	}

	public record QuestionGradeItem(
			long questionId,
			BigDecimal points,
			String feedback
	) {}
}
