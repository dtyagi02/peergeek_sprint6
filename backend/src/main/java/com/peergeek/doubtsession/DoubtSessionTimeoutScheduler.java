package com.peergeek.doubtsession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class DoubtSessionTimeoutScheduler {
	private static final Logger log = LoggerFactory.getLogger(DoubtSessionTimeoutScheduler.class);

	private final DoubtSessionService doubtSessionService;

	public DoubtSessionTimeoutScheduler(DoubtSessionService doubtSessionService) {
		this.doubtSessionService = doubtSessionService;
	}

	@Scheduled(fixedDelay = 5000)
	public void processSessionTimeouts() {
		try {
			doubtSessionService.processTimeouts();
		} catch (Exception ex) {
			log.error("Error processing doubt session timeouts: {}", ex.getMessage(), ex);
		}
	}
}
