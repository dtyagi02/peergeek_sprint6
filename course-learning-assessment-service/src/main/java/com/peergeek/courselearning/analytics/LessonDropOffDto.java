package com.peergeek.courselearning.analytics;

public record LessonDropOffDto(
        long lessonId,
        String lessonTitle,
        int orderIndex,
        long reachedCount,
        long completedCount,
        double completionRate) {}
