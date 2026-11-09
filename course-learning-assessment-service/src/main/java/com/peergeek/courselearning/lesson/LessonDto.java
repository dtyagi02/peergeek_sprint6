package com.peergeek.courselearning.lesson;

import java.time.Instant;

public record LessonDto(
        Long id,
        Long moduleId,
        String title,
        String summary,
        int position,
        Instant createdAt,
        Instant updatedAt
) {
    public static LessonDto from(LessonEntity lesson) {
        return new LessonDto(
                lesson.getId(),
                lesson.getModuleId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getPosition(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt()
        );
    }
}

