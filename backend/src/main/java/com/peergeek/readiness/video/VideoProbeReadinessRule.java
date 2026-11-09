package com.peergeek.readiness.video;

import java.util.Optional;

public class VideoProbeReadinessRule {
	private final VideoResourceProbeService probeService;

	public VideoProbeReadinessRule(VideoResourceProbeService probeService) {
		this.probeService = probeService;
	}

	public Optional<VideoProbeIssue> evaluate(long resourceId, String checksumSha256) {
		VideoProbeOutcome outcome = probeService.probe(resourceId, checksumSha256);
		if (outcome.valid()) {
			return Optional.empty();
		}
		if (VideoProbeException.Outcome.VIDEO_INVALID.name().equals(outcome.errorCode())) {
			return Optional.of(new VideoProbeIssue(
					VideoProbeException.Outcome.VIDEO_INVALID.name(),
					"The video resource is malformed, has no video stream, or has no positive duration."));
		}
		if (VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE.name().equals(outcome.errorCode())) {
			return Optional.of(new VideoProbeIssue(
					VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE.name(),
					"The video resource could not be checked because ffprobe is unavailable."));
		}
		throw new IllegalStateException("Unexpected video probe error code: " + outcome.errorCode());
	}
}
