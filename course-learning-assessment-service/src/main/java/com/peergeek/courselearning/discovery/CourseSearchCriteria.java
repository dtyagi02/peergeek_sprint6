package com.peergeek.courselearning.discovery;

import java.util.List;

public record CourseSearchCriteria(
        String q,
        String categorySlug,
        List<String> difficulties,
        String sort,
        int page,
        int size,
        int minCompletionSample) {

    public CourseSearchCriteria {
        difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
    }
}
