package com.peergeek.courselearning.course;

import com.peergeek.courselearning.common.CourseDifficulty;
import com.peergeek.courselearning.common.CourseStatus;

import java.time.Instant;
import java.util.List;

public record CourseDto(
        Long id,
        Long ownerId,
        String title,
        String description,
        CategoryDto category,
        CourseDifficulty difficulty,
        CourseStatus status,
        List<String> topics,
        long moduleCount,
        long lessonCount,
        long totalWordCount,
        Instant contentUpdatedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public CourseDto {
        topics = topics != null ? List.copyOf(topics) : List.of();
    }
}

