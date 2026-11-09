package com.peergeek.doubtsession.entity;

public enum DoubtSessionStatus {
	REQUESTED,
	ACCEPTED,
	IN_PROGRESS,
	COMPLETED,
	REJECTED,
	CANCELLED,
	EXPIRED,
	INTERRUPTED;

	public boolean isActive() {
		return this == REQUESTED || this == ACCEPTED || this == IN_PROGRESS;
	}

	public boolean isTerminal() {
		return this == COMPLETED || this == REJECTED || this == CANCELLED || this == EXPIRED || this == INTERRUPTED;
	}
}

