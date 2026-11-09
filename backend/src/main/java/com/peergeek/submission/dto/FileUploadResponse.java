package com.peergeek.submission.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record FileUploadResponse(
		long fileId,
		UploadDetails upload
) {
	public record UploadDetails(
			URI url,
			String method,
			Map<String, String> headers,
			Instant expiresAt
	) {
		public UploadDetails {
			headers = headers == null ? Map.of() : Map.copyOf(headers);
		}
	}
}
