package com.peergeek.lesson;

import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;

public record LessonContentDto(String contentFormat, JsonNode document, int wordCount, long version, Instant updatedAt) {
	public LessonContentDto {
		document = document.deepCopy();
	}
}
