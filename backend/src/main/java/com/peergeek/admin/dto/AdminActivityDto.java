package com.peergeek.admin.dto;

import java.time.Instant;
import java.util.Map;

public record AdminActivityDto(
		Long id,
		String action,
		Long actorId,
		String actorName,
		String actorRole,
		String entityType,
		String entityId,
		String title,
		String description,
		Map<String, Object> details,
		Instant createdAt) {
}
