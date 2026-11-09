package com.peergeek.referral.security;

import java.util.Optional;
import com.peergeek.referral.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

	public Optional<CurrentUser> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof CurrentUserPrincipal p) {
			return Optional.of(new CurrentUser(p.getId(), p.getRole()));
		}
		if (principal instanceof CurrentUser c) {
			return Optional.of(c);
		}
		return Optional.empty();
	}

	public CurrentUser requireCurrentUser() {
		return currentUser().orElseThrow(() -> new ForbiddenException("Authentication required"));
	}
}

