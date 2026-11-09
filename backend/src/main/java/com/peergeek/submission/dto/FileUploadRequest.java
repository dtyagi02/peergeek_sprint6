package com.peergeek.submission.dto;

public record FileUploadRequest(
		String filename,
		String mimeType,
		long sizeBytes
) {}
