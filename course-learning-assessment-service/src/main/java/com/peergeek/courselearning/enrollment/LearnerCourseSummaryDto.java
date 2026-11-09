package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.CourseDifficulty;

import java.util.List;

public record LearnerCourseSummaryDto(
        Long id,
        String title,
        String description,
        String category,
        CourseDifficulty difficulty,
        List<String> topics,
        InstructorSummaryDto instructor,
        String courseStatus
) {
    public LearnerCourseSummaryDto {
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}

