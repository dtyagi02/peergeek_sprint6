package com.peergeek.security;

import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class OwnershipGuard {
	public boolean isOwner(Long ownerUserId, Long currentUserId) {
		return ownerUserId != null && ownerUserId.equals(currentUserId);
	}

	public void requireOwner(Long ownerUserId, Long currentUserId) {
		if (!isOwner(ownerUserId, currentUserId)) {
			throw new ForbiddenException("You do not have permission to access this resource");
		}
	}

	public void requireOwnerOrNotFound(Long ownerUserId, Long currentUserId) {
		if (!isOwner(ownerUserId, currentUserId)) {
			throw new NotFoundException("The requested resource was not found");
		}
	}
}
