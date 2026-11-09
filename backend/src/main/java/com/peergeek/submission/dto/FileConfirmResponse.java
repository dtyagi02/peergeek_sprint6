package com.peergeek.submission.dto;

public record FileConfirmResponse(
		long fileId,
		String status,
		String failureReason
) {}
