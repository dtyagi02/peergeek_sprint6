package com.peergeek.enrollment;

import java.time.Instant;

public record PresignedUrlDto(
		String url,
		Instant expiresAt) {
}
