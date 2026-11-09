package com.peergeek.courselearning.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.courselearning.common.CourseDifficulty;

import java.net.URI;
import java.util.List;

public record CoursePreview(
        long courseId,
        String title,
        String description,
        String categoryName,
        CourseDifficulty difficulty,
        List<String> topics,
        List<Module> modules
) {
    public CoursePreview {
        topics = topics != null ? List.copyOf(topics) : List.of();
        modules = modules != null ? List.copyOf(modules) : List.of();
    }

    public record Module(long id, String title, String description, List<Lesson> lessons) {
        public Module {
            lessons = lessons != null ? List.copyOf(lessons) : List.of();
        }
    }

    public record Lesson(long id, String title, String summary, JsonNode document, List<Resource> resources) {
        public Lesson {
            document = document != null ? document.deepCopy() : null;
            resources = resources != null ? List.copyOf(resources) : List.of();
        }
    }

    public record Resource(long id, String type, String title, String mimeType, Long sizeBytes, URI contentUrl, URI externalUrl) {
    }
}

