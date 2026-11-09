package com.peergeek.lesson;

public record LessonOutlineRow(
		Long id, Long moduleId, String title, String summary, int position, boolean hasContent, int wordCount) {
}
