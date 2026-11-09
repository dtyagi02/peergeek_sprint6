package com.peergeek.courselearning.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record LearnerLessonDto(
        LearnerLessonDetailDto lesson,
        JsonNode contentDocument,
        List<LearnerResourceDto> resources,
        boolean completed,
        Long previousLessonId,
        Long nextLessonId
) {
    public LearnerLessonDto {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}

