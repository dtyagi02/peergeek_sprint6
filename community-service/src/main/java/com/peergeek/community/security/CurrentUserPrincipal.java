package com.peergeek.community.security;

import com.peergeek.community.common.UserRole;
import java.security.Principal;

public record CurrentUserPrincipal(Long id, UserRole role) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(id);
    }
}

