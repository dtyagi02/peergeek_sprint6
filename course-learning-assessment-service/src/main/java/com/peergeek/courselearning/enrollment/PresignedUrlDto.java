package com.peergeek.courselearning.enrollment;

import java.time.Instant;

public record PresignedUrlDto(
        String url,
        Instant expiresAt
) {}

