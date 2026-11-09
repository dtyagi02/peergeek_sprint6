package com.peergeek.courselearning.lesson;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LessonContentDto(
        String contentFormat,
        JsonNode document,
        int wordCount,
        long version,
        Instant updatedAt
) {
    public LessonContentDto {
        if (document != null) {
            document = document.deepCopy();
        }
    }
}

