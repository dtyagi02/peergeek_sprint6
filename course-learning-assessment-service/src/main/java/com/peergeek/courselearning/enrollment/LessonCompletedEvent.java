package com.peergeek.courselearning.enrollment;

public record LessonCompletedEvent(
        long courseId,
        long userId,
        long lessonId,
        int completedLessons,
        int totalLessons
) {}

