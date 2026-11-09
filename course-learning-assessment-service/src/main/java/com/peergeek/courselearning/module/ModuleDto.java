package com.peergeek.courselearning.module;

public record ModuleDto(
        Long id,
        Long courseId,
        String title,
        String description,
        int position
) {
    public static ModuleDto fromEntity(CourseModuleEntity entity) {
        return new ModuleDto(
                entity.getId(),
                entity.getCourseId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPosition()
        );
    }
}

