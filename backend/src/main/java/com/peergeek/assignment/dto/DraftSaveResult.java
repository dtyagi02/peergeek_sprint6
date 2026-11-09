package com.peergeek.assignment.dto;

import java.time.Instant;

public record DraftSaveResult(
		long version,
		Instant savedAt
) {}
