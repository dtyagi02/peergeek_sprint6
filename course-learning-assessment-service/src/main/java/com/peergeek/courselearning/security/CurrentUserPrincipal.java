package com.peergeek.courselearning.security;

import com.peergeek.courselearning.common.UserRole;
import java.security.Principal;

public record CurrentUserPrincipal(Long id, UserRole role) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(id);
    }
}

