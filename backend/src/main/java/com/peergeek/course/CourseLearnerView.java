package com.peergeek.course;

import java.net.URI;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public record CourseLearnerView(
		long courseId,
		String title,
		String description,
		String categoryName,
		CourseDifficulty difficulty,
		List<String> topics,
		List<Module> modules) {
	public CourseLearnerView {
		topics = List.copyOf(topics);
		modules = List.copyOf(modules);
	}

	public record Module(long id, String title, String description, List<Lesson> lessons) {
		public Module {
			lessons = List.copyOf(lessons);
		}
	}

	public record Lesson(
			long id, String title, String summary, String contentFormat, JsonNode document,
			List<Resource> resources) {
		public Lesson {
			document = document.deepCopy();
			resources = List.copyOf(resources);
		}
	}

	public record Resource(
			long id, String type, String title, String mimeType, Long sizeBytes, URI externalUrl) {
	}
}
