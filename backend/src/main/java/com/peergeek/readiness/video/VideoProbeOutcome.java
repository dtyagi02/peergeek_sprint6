package com.peergeek.readiness.video;

public record VideoProbeOutcome(
	boolean valid,
	String errorCode,
	Long durationMilliseconds,
	Integer width,
	Integer height,
	String codec) {
	public static VideoProbeOutcome valid(VideoMetadata metadata) {
		return new VideoProbeOutcome(
				true, null, metadata.durationMilliseconds(), metadata.width(), metadata.height(), metadata.codec());
	}

	public static VideoProbeOutcome invalid(String errorCode) {
		return new VideoProbeOutcome(false, errorCode, null, null, null, null);
	}
}
