package com.peergeek.identity.dto;

import java.time.Instant;
import com.peergeek.identity.entity.UserMode;
import com.peergeek.identity.entity.UserRole;

public record AdminUserDto(
		Long id,
		String email,
		String displayName,
		UserRole role,
		UserMode lastMode,
		boolean enabled,
		boolean emailVerified,
		Instant createdAt) {
}

