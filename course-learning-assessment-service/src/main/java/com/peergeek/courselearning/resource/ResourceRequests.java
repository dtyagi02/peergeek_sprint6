package com.peergeek.courselearning.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ResourceRequests {

    public record Link(String url, String title) {}

    public record Update(String title, String url) {}

    public record Order(List<Long> resourceIds) {}

    public record Expected(String resourceType, int minCount, String label) {}

    public record LessonUpload(
            String filename,
            String mimeType,
            long sizeBytes,
            String type,
            String title
    ) {}

    public record ThumbnailUpload(
            String filename,
            String mimeType,
            long sizeBytes,
            String title
    ) {}

    public record UploadInstruction(
            String method,
            String url,
            Map<String, String> headers
    ) {}

    public record UploadCreated(
            Long resourceId,
            UploadInstruction upload
    ) {}

    public record DownloadUrl(
            String url,
            Instant expiresAt
    ) {}

    public record ThumbnailDto(
            Long resourceId,
            String status,
            String url,
            Instant expiresAt
    ) {}

    public record ResourceSnapshot(
            Long id,
            Long lessonId,
            String type,
            String title,
            String status,
            int position,
            String externalUrl,
            String filename,
            Long sizeBytes,
            String mimeType
    ) {
        public static ResourceSnapshot from(ResourceEntity r) {
            return new ResourceSnapshot(
                    r.getId(),
                    r.getLessonId(),
                    r.getType(),
                    r.getTitle(),
                    r.getStatus(),
                    r.getPosition(),
                    r.getExternalUrl(),
                    r.getOriginalFilename(),
                    r.getSizeBytes(),
                    r.getMimeType()
            );
        }
    }

    public record ExpectedResourceSnapshot(
            Long id,
            Long lessonId,
            String resourceType,
            int minCount,
            String label
    ) {
        public static ExpectedResourceSnapshot from(LessonExpectedResourceEntity e) {
            return new ExpectedResourceSnapshot(
                    e.getId(),
                    e.getLessonId(),
                    e.getResourceType(),
                    e.getMinCount(),
                    e.getLabel()
            );
        }
    }
}

