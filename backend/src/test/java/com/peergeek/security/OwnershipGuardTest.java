package com.peergeek.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;

class OwnershipGuardTest {
	private final OwnershipGuard guard = new OwnershipGuard();

	@Test
	void ownerMayProceedAndOwnershipCanBeCheckedWithoutThrowing() {
		guard.requireOwner(7L, 7L);
		assertThat(guard.isOwner(7L, 7L)).isTrue();
		assertThat(guard.isOwner(7L, 8L)).isFalse();
	}

	@Test
	void nonOwnerCanReceiveForbiddenOrNotFoundToHideResourceExistence() {
		assertThatThrownBy(() -> guard.requireOwner(7L, 8L))
				.isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> guard.requireOwnerOrNotFound(7L, 8L))
				.isInstanceOf(NotFoundException.class);
	}
}
