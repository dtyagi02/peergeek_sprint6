package com.peergeek.courselearning.enrollment;

import java.time.Instant;

public record CourseProgressDto(
        int completedLessons,
        int totalLessons,
        int percent,
        Instant completedAt
) {}

