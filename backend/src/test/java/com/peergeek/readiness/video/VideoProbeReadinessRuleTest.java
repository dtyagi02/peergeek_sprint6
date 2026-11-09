package com.peergeek.readiness.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class VideoProbeReadinessRuleTest {
	@Test
	void mapsInvalidVideoToTheStableReadinessRuleCode() {
		VideoResourceProbeService service = mock(VideoResourceProbeService.class);
		when(service.probe(11, "c".repeat(64)))
				.thenReturn(VideoProbeOutcome.invalid("VIDEO_INVALID"));

		assertThat(new VideoProbeReadinessRule(service).evaluate(11, "c".repeat(64)))
				.contains(new VideoProbeIssue(
						"VIDEO_INVALID",
						"The video resource is malformed, has no video stream, or has no positive duration."));
	}

	@Test
	void mapsUnavailableProbeToItsOwnStableReadinessRuleCode() {
		VideoResourceProbeService service = mock(VideoResourceProbeService.class);
		when(service.probe(11, "c".repeat(64)))
				.thenReturn(VideoProbeOutcome.invalid("VIDEO_PROBE_UNAVAILABLE"));

		assertThat(new VideoProbeReadinessRule(service).evaluate(11, "c".repeat(64)))
				.contains(new VideoProbeIssue(
						"VIDEO_PROBE_UNAVAILABLE",
						"The video resource could not be checked because ffprobe is unavailable."));
	}

	@Test
	void returnsNoIssueForValidVideo() {
		VideoResourceProbeService service = mock(VideoResourceProbeService.class);
		when(service.probe(11, "c".repeat(64)))
				.thenReturn(new VideoProbeOutcome(true, null, 1_000L, 2, 2, "rawvideo"));

		assertThat(new VideoProbeReadinessRule(service).evaluate(11, "c".repeat(64))).isEmpty();
	}
}
