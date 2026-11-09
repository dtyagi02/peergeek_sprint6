package com.peergeek.courselearning.bookmark;

import java.time.Instant;

public class BookmarkDtos {

    public record BookmarkDto(
            long id,
            String targetType,
            long courseId,
            String courseTitle,
            String courseDifficulty,
            Long lessonId,
            String lessonTitle,
            String moduleTitle,
            Instant createdAt
    ) {}

    public record BookmarkStatusDto(
            boolean bookmarked,
            Long bookmarkId
    ) {}
}

