package com.peergeek.readiness.video;

import java.time.Instant;

public record ResourceProbeResult(
	long resourceId,
	String checksumSha256,
	boolean valid,
	String errorCode,
	Long durationMilliseconds,
	Integer width,
	Integer height,
	String codec,
	Instant probedAt) {

	public VideoProbeOutcome outcome() {
		return new VideoProbeOutcome(valid, errorCode, durationMilliseconds, width, height, codec);
	}
}
