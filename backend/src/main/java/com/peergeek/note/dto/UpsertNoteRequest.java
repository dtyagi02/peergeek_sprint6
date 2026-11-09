package com.peergeek.note.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertNoteRequest(
		@NotNull(message = "Note content must not be null")
		@Size(max = 50000, message = "Note content cannot exceed 50000 characters")
		String content
) {}
