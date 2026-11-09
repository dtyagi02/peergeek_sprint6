package com.peergeek.courselearning.enrollment;

import java.time.Instant;

public record LearnerResourceDto(
        Long id,
        String type,
        String title,
        String mimeType,
        Long sizeBytes,
        String url,
        Instant expiresAt,
        String externalUrl
) {}

