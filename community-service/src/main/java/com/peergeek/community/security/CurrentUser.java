package com.peergeek.community.security;

import com.peergeek.community.common.UserRole;

public record CurrentUser(Long id, UserRole role) {
}

