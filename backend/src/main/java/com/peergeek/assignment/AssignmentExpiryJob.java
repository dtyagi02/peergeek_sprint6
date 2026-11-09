package com.peergeek.assignment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.peergeek.config.AssignmentSettings;
import com.peergeek.submission.LearnerSubmissionService;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionRepository;
import com.peergeek.submission.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssignmentExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(AssignmentExpiryJob.class);
	private static final int BATCH_SIZE = 50;

	private final SubmissionRepository submissionRepository;
	private final LearnerSubmissionService submissionService;
	private final AssignmentSettings settings;
	private final Clock clock;

	public AssignmentExpiryJob(
			SubmissionRepository submissionRepository,
			LearnerSubmissionService submissionService,
			AssignmentSettings settings,
			Clock clock) {
		this.submissionRepository = submissionRepository;
		this.submissionService = submissionService;
		this.settings = settings;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${peergeek.assignment.expiry-job-interval-ms:60000}")
	public void run() {
		Instant threshold = clock.instant().minus(Duration.ofSeconds(settings.getGraceSeconds()));
		int finalizedCount = 0;

		while (true) {
			List<SubmissionEntity> expired = submissionRepository.findExpiredInProgress(
					SubmissionStatus.IN_PROGRESS,
					threshold,
					PageRequest.of(0, BATCH_SIZE));

			if (expired.isEmpty()) {
				break;
			}

			for (SubmissionEntity s : expired) {
				try {
					boolean finalized = submissionService.finalizeExpiredSubmission(s.getId());
					if (finalized) {
						finalizedCount++;
					}
				} catch (Exception ex) {
					log.warn("Error finalizing expired submission id={}", s.getId(), ex);
				}
			}

			if (expired.size() < BATCH_SIZE) {
				break;
			}
		}

		if (finalizedCount > 0) {
			log.info("Finalized {} expired submissions", finalizedCount);
		}
	}
}
