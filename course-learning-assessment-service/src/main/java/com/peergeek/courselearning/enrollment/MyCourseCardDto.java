package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.CourseDifficulty;
import com.peergeek.courselearning.course.CategoryDto;

import java.time.Instant;

public record MyCourseCardDto(
        Long courseId,
        String title,
        String thumbnailUrl,
        InstructorSummaryDto instructor,
        CourseDifficulty difficulty,
        String courseStatus,
        CourseProgressDto progress,
        Instant lastAccessedAt,
        Long continueLessonId,
        CategoryDto category
) {}

