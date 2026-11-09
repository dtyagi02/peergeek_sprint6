package com.peergeek.community.security;

import com.peergeek.community.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserProvider {

    public Optional<CurrentUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        if (auth != null && auth.getPrincipal() instanceof CurrentUserPrincipal p) {
            return Optional.of(new CurrentUser(p.id(), p.role()));
        }
        return Optional.empty();
    }

    public CurrentUser requireCurrentUser() {
        return currentUser()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));
    }
}

