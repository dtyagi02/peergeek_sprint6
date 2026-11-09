package com.peergeek.security;

import com.peergeek.user.UserRole;

public record CurrentUser(Long id, UserRole role) {
}
