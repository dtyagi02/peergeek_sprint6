package com.peergeek.readiness.video;

import com.peergeek.file.InternalResourceUrlException;
import com.peergeek.file.InternalResourceUrlProvider;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VideoResourceProbeService {
	private static final Logger LOGGER = LoggerFactory.getLogger(VideoResourceProbeService.class);

	private final InternalResourceUrlProvider internalUrlProvider;
	private final FfprobeRunner ffprobeRunner;
	private final ResourceProbeResultCache cache;

	public VideoResourceProbeService(
			InternalResourceUrlProvider internalUrlProvider,
			FfprobeRunner ffprobeRunner,
			ResourceProbeResultCache cache) {
		this.internalUrlProvider = internalUrlProvider;
		this.ffprobeRunner = ffprobeRunner;
		this.cache = cache;
	}

	public VideoProbeOutcome probe(long resourceId, String checksumSha256) {
		if (resourceId <= 0 || checksumSha256 == null || !checksumSha256.matches("[a-fA-F0-9]{64}")) {
			throw new IllegalArgumentException("A positive resource ID and SHA-256 checksum are required");
		}
		var cached = cache.findVideo(resourceId, checksumSha256);
		if (cached.isPresent()) {
			return cached.get().outcome();
		}

		try {
			URI internalUrl = internalUrlProvider.createInternalPresignedGetUrl(resourceId);
			VideoProbeOutcome outcome = VideoProbeOutcome.valid(ffprobeRunner.probe(internalUrl));
			cache.saveVideo(resourceId, checksumSha256, outcome);
			return outcome;
		} catch (InternalResourceUrlException exception) {
			LOGGER.warn("Could not create an internal resource URL for resource {}", resourceId);
			return VideoProbeOutcome.invalid(VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE.name());
		} catch (VideoProbeException exception) {
			VideoProbeOutcome outcome = VideoProbeOutcome.invalid(exception.outcome().name());
			if (exception.outcome() == VideoProbeException.Outcome.VIDEO_INVALID) {
				cache.saveVideo(resourceId, checksumSha256, outcome);
			}
			return outcome;
		}
	}
}
