package com.peergeek.courselearning.enrollment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LearnerCourseOutlineDto(
        LearnerCourseSummaryDto course,
        List<LearnerModuleDto> modules,
        CourseProgressDto progress,
        @JsonProperty("continue") ContinueLessonDto continueLesson
) {
    public LearnerCourseOutlineDto {
        modules = modules == null ? List.of() : List.copyOf(modules);
    }
}

