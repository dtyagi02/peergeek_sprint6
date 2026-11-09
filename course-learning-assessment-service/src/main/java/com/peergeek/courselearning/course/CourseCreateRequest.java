package com.peergeek.courselearning.course;

import com.peergeek.courselearning.common.CourseDifficulty;
import java.util.List;

public record CourseCreateRequest(
        String title,
        String description,
        Long categoryId,
        CourseDifficulty difficulty,
        List<String> topics
) {}

