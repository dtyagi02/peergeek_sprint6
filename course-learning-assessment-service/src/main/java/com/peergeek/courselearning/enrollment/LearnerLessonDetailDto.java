package com.peergeek.courselearning.enrollment;

public record LearnerLessonDetailDto(
        Long id,
        String title,
        String summary,
        Long moduleId
) {}

