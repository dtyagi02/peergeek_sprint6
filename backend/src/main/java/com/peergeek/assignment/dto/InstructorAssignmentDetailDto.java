package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.peergeek.assignment.AssignmentKind;
import com.peergeek.assignment.AssignmentStatus;

public record InstructorAssignmentDetailDto(
		Long id,
		Long courseId,
		Long moduleId,
		Long lessonId,
		AssignmentKind kind,
		String title,
		String instructions,
		AssignmentStatus status,
		BigDecimal maxPoints,
		Instant opensAt,
		Instant dueAt,
		Integer timeLimitMinutes,
		boolean allowLate,
		boolean allowText,
		boolean allowFiles,
		boolean showCorrectAnswers,
		int attemptCount,
		Instant publishedAt,
		long version,
		List<QuestionDto> questions) {
}
