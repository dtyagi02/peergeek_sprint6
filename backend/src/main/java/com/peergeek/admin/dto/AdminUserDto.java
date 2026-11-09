package com.peergeek.admin.dto;

import java.time.Instant;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRole;

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
