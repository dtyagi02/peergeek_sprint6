package com.peergeek.courselearning.discovery;

import java.time.Instant;

public record CourseCardDto(
        Long id,
        String title,
        String shortDescription,
        String thumbnailUrl,
        CategoryCardDto category,
        String difficulty,
        InstructorCardDto instructor,
        int moduleCount,
        int lessonCount,
        Instant publishedAt,
        CourseStatsDto stats) {

    public record CategoryCardDto(Long id, String name, String slug) {}
    public record InstructorCardDto(Long id, String displayName) {}
}
