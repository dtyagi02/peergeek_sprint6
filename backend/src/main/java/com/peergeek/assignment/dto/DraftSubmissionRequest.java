package com.peergeek.assignment.dto;

import java.util.List;

public record DraftSubmissionRequest(
		long version,
		String textAnswer,
		List<DraftAnswerItem> answers
) {
	public DraftSubmissionRequest {
		answers = answers == null ? List.of() : List.copyOf(answers);
	}

	public record DraftAnswerItem(
			long questionId,
			String answerText,
			List<String> selectedOptionIds
	) {
		public DraftAnswerItem {
			selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
		}
	}
}
