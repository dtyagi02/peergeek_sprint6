package com.peergeek.module;

import java.time.Instant;

public record ModuleDto(Long id, Long courseId, String title, String description, int position,
		Instant createdAt, Instant updatedAt) {
	public static ModuleDto from(ModuleEntity module) {
		return new ModuleDto(module.getId(), module.getCourse().getId(), module.getTitle(),
				module.getDescription(), module.getPosition(), module.getCreatedAt(), module.getUpdatedAt());
	}
}
