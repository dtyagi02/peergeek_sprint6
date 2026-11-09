package com.peergeek.courselearning.course;

public record CategoryDto(
        Long id,
        String name,
        String slug,
        int sortOrder,
        boolean active
) {
    public static CategoryDto fromEntity(CategoryEntity entity) {
        return new CategoryDto(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getSortOrder(),
                entity.isActive()
        );
    }
}

