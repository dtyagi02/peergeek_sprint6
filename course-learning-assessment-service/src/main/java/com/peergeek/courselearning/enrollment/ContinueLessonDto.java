package com.peergeek.courselearning.enrollment;

public record ContinueLessonDto(
        Long lessonId,
        ContinueReason reason
) {}

