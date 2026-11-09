package com.peergeek.readiness.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peergeek.file.InternalResourceUrlException;
import com.peergeek.file.InternalResourceUrlProvider;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VideoResourceProbeServiceTest {
	private static final String CHECKSUM = "a".repeat(64);

	@Test
	void usesCachedResultWithoutRequestingUrlOrRunningFfprobe() {
		InternalResourceUrlProvider urlProvider = mock(InternalResourceUrlProvider.class);
		FfprobeRunner runner = mock(FfprobeRunner.class);
		ResourceProbeResultCache cache = mock(ResourceProbeResultCache.class);
		ResourceProbeResult cached = new ResourceProbeResult(
				17, CHECKSUM, true, null, 1_234L, 640, 360, "h264", Instant.EPOCH);
		when(cache.findVideo(17, CHECKSUM)).thenReturn(Optional.of(cached));

		VideoProbeOutcome outcome = new VideoResourceProbeService(urlProvider, runner, cache)
				.probe(17, CHECKSUM);

		assertThat(outcome).isEqualTo(new VideoProbeOutcome(true, null, 1_234L, 640, 360, "h264"));
		verify(urlProvider, never()).createInternalPresignedGetUrl(17);
		verify(runner, never()).probe(URI.create("http://example.test/internal-presigned"));
	}

	@Test
	void probesOnlyTheInternalPresignedUrlAndCachesSuccess() {
		InternalResourceUrlProvider urlProvider = mock(InternalResourceUrlProvider.class);
		FfprobeRunner runner = mock(FfprobeRunner.class);
		ResourceProbeResultCache cache = mock(ResourceProbeResultCache.class);
		URI internalUrl = URI.create("http://minio.internal/bucket/object?signature=short-lived");
		when(cache.findVideo(17, CHECKSUM)).thenReturn(Optional.empty());
		when(urlProvider.createInternalPresignedGetUrl(17)).thenReturn(internalUrl);
		when(runner.probe(internalUrl)).thenReturn(new VideoMetadata(5_000, 1280, 720, "h264"));

		VideoProbeOutcome outcome = new VideoResourceProbeService(urlProvider, runner, cache)
				.probe(17, CHECKSUM);

		assertThat(outcome).isEqualTo(new VideoProbeOutcome(true, null, 5_000L, 1280, 720, "h264"));
		verify(runner).probe(internalUrl);
		verify(cache).saveVideo(17, CHECKSUM, outcome);
	}

	@Test
	void doesNotCacheProbeUnavailableFailures() {
		InternalResourceUrlProvider urlProvider = mock(InternalResourceUrlProvider.class);
		FfprobeRunner runner = mock(FfprobeRunner.class);
		ResourceProbeResultCache cache = mock(ResourceProbeResultCache.class);
		URI internalUrl = URI.create("http://minio.internal/bucket/object?signature=short-lived");
		when(cache.findVideo(17, CHECKSUM)).thenReturn(Optional.empty());
		when(urlProvider.createInternalPresignedGetUrl(17)).thenReturn(internalUrl);
		when(runner.probe(internalUrl)).thenThrow(new VideoProbeException(
				VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE, "ffprobe unavailable"));

		assertThat(new VideoResourceProbeService(urlProvider, runner, cache).probe(17, CHECKSUM))
				.isEqualTo(VideoProbeOutcome.invalid("VIDEO_PROBE_UNAVAILABLE"));
		verify(cache, never()).saveVideo(17, CHECKSUM,
				VideoProbeOutcome.invalid("VIDEO_PROBE_UNAVAILABLE"));
	}

	@Test
	void reportsInternalUrlFailuresAsUnavailableAndDoesNotCacheThem() {
		InternalResourceUrlProvider urlProvider = mock(InternalResourceUrlProvider.class);
		FfprobeRunner runner = mock(FfprobeRunner.class);
		ResourceProbeResultCache cache = mock(ResourceProbeResultCache.class);
		when(cache.findVideo(17, CHECKSUM)).thenReturn(Optional.empty());
		when(urlProvider.createInternalPresignedGetUrl(17))
				.thenThrow(new InternalResourceUrlException("resource URL unavailable", null));

		assertThat(new VideoResourceProbeService(urlProvider, runner, cache).probe(17, CHECKSUM))
				.isEqualTo(VideoProbeOutcome.invalid("VIDEO_PROBE_UNAVAILABLE"));
		verify(runner, never()).probe(URI.create("http://example.test/internal-presigned"));
		verify(cache, never()).saveVideo(17, CHECKSUM,
				VideoProbeOutcome.invalid("VIDEO_PROBE_UNAVAILABLE"));
	}
}
