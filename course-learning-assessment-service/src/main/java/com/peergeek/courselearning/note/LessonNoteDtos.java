package com.peergeek.courselearning.note;

import java.time.Instant;

public class LessonNoteDtos {

    public record LessonNoteDto(
            Long id,
            Long courseId,
            Long lessonId,
            Long userId,
            String content,
            Instant updatedAt
    ) {
        public static LessonNoteDto from(LessonNoteEntity entity) {
            return new LessonNoteDto(
                    entity.getId(),
                    entity.getCourseId(),
                    entity.getLessonId(),
                    entity.getUserId(),
                    entity.getContent(),
                    entity.getUpdatedAt()
            );
        }
    }

    public record UpsertNoteRequest(String content) {}
}

