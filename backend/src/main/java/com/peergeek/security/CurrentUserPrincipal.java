package com.peergeek.security;

import com.peergeek.user.UserRole;

public record CurrentUserPrincipal(Long id, UserRole role) {
	@Override
	public String toString() {
		return id == null ? "" : id.toString();
	}
}
