package com.peergeek.enrollment;

import java.time.Instant;
import com.peergeek.file.ResourceType;

public record LearnerResourceDto(
		Long id,
		ResourceType type,
		String title,
		String mimeType,
		Long sizeBytes,
		String url,
		Instant expiresAt,
		String externalUrl) {
}
