package com.peergeek.lesson;

import com.fasterxml.jackson.databind.JsonNode;

public record LessonContentSaveRequest(JsonNode document, Long baseVersion) {
}
