package com.peergeek.course;

import java.util.List;

public record CourseOutline(Long courseId, List<ModuleOutline> modules) {
	public CourseOutline {
		modules = List.copyOf(modules);
	}

	public record ModuleOutline(
			Long id, String title, String description, int position, List<LessonOutline> lessons) {
		public ModuleOutline {
			lessons = List.copyOf(lessons);
		}
	}

	public record LessonOutline(
			Long id, String title, String summary, int position, boolean hasContent, int wordCount) {
	}
}
