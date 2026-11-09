package com.peergeek.analytics.dto;

public record LessonDropOffDto(
		long lessonId,
		String lessonTitle,
		String moduleTitle,
		int orderIndex,
		long completedCount,
		double completionPercent,
		double dropOffRate) {
}
