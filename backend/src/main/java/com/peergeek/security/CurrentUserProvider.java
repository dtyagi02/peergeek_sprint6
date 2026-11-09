package com.peergeek.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
	public Optional<CurrentUser> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserPrincipal principal)) {
			return Optional.empty();
		}
		return Optional.of(new CurrentUser(principal.id(), principal.role()));
	}

	public CurrentUser requireCurrentUser() {
		return currentUser().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
				"An authenticated user is required"));
	}
}
