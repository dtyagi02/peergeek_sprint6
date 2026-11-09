package com.peergeek.readiness;

import java.time.Instant;
import com.peergeek.config.ReadinessSettings;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReadinessRunMaintenance {
	private final ReadinessRunStore runs;
	private final ReadinessSettings settings;

	public ReadinessRunMaintenance(ReadinessRunStore runs, ReadinessSettings settings) {
		this.runs = runs;
		this.settings = settings;
	}

	@Scheduled(fixedDelayString = "${readiness.runs.maintenance-interval:PT1M}")
	public void markTimedOutRuns() {
		runs.failTimedOut(Instant.now().minus(settings.getRuns().getTimeout()));
	}
}
