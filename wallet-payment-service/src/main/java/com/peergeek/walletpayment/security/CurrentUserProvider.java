package com.peergeek.walletpayment.security;

import com.peergeek.walletpayment.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserProvider {

    public Optional<CurrentUser> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public CurrentUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));
    }
}

