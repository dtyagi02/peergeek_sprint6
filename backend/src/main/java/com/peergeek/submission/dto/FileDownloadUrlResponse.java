package com.peergeek.submission.dto;

import java.net.URI;
import java.time.Instant;

public record FileDownloadUrlResponse(
		URI downloadUrl,
		Instant expiresAt
) {}
