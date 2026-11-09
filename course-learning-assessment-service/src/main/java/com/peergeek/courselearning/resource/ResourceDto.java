package com.peergeek.courselearning.resource;

import com.peergeek.courselearning.common.ResourcePurpose;
import com.peergeek.courselearning.common.ResourceStatus;
import com.peergeek.courselearning.common.ResourceType;

import java.time.Instant;

public record ResourceDto(
        Long id,
        Long courseId,
        Long lessonId,
        String purpose,
        String type,
        String title,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
        String externalUrl,
        int position,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static ResourceDto fromEntity(ResourceEntity entity) {
        return new ResourceDto(
                entity.getId(),
                entity.getCourseId(),
                entity.getLessonId(),
                entity.getPurpose(),
                entity.getType(),
                entity.getTitle(),
                entity.getOriginalFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                entity.getExternalUrl(),
                entity.getPosition(),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

