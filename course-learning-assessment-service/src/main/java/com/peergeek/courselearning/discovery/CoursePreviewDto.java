package com.peergeek.courselearning.discovery;

import java.time.Instant;
import java.util.List;

public record CoursePreviewDto(
        Long id,
        String title,
        String shortDescription,
        String description,
        String thumbnailUrl,
        CourseCardDto.CategoryCardDto category,
        String difficulty,
        List<String> topics,
        InstructorPreviewDto instructor,
        int moduleCount,
        int lessonCount,
        Instant publishedAt,
        CourseStatsDto stats,
        List<ModulePreviewDto> outline) {

    public CoursePreviewDto {
        topics = topics == null ? List.of() : List.copyOf(topics);
        outline = outline == null ? List.of() : List.copyOf(outline);
    }

    public record InstructorPreviewDto(Long id, String displayName, String headline, String bio) {}
    public record ModulePreviewDto(String moduleTitle, List<LessonPreviewDto> lessons) {
        public ModulePreviewDto {
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }
    public record LessonPreviewDto(String title) {}
}
