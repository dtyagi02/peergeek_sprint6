package com.peergeek.identity.security;

import com.peergeek.identity.entity.UserRole;

public record CurrentUser(Long id, UserRole role) {
}

