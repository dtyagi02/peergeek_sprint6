package com.peergeek.readiness.video;

public final class VideoProbeException extends RuntimeException {
	public enum Outcome {
		VIDEO_INVALID,
		VIDEO_PROBE_UNAVAILABLE
	}

	private final Outcome outcome;

	public VideoProbeException(Outcome outcome, String message) {
		super(message);
		this.outcome = outcome;
	}

	public VideoProbeException(Outcome outcome, String message, Throwable cause) {
		super(message, cause);
		this.outcome = outcome;
	}

	public Outcome outcome() {
		return outcome;
	}
}
