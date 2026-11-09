package com.peergeek.courselearning.course;

import java.util.List;

public record CourseOutline(Long courseId, List<ModuleOutline> modules) {
    public CourseOutline {
        modules = modules != null ? List.copyOf(modules) : List.of();
    }

    public record ModuleOutline(
            Long id, String title, String description, int position, List<LessonOutline> lessons) {
        public ModuleOutline {
            lessons = lessons != null ? List.copyOf(lessons) : List.of();
        }
    }

    public record LessonOutline(
            Long id, String title, String summary, int position, boolean hasContent, int wordCount) {
    }
}

