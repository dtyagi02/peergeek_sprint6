package com.peergeek.courselearning.enrollment;

import java.util.List;

public record LearnerModuleDto(
        Long id,
        String title,
        String description,
        int position,
        List<LearnerLessonSummaryDto> lessons
) {
    public LearnerModuleDto {
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
    }
}

