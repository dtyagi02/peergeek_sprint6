package com.peergeek.course;

import java.util.List;

public record PublishedModuleOutline(String title, List<PublishedLessonOutline> lessons) {
	public PublishedModuleOutline {
		lessons = List.copyOf(lessons);
	}
}
