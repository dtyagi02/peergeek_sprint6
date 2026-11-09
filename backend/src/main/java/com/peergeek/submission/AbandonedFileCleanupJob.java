package com.peergeek.submission;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AbandonedFileCleanupJob {

	private final SubmissionFileService submissionFileService;

	public AbandonedFileCleanupJob(SubmissionFileService submissionFileService) {
		this.submissionFileService = submissionFileService;
	}

	@Scheduled(fixedDelayString = "${peergeek.assignment.abandoned-cleanup-interval-ms:3600000}")
	public void run() {
		submissionFileService.cleanupAbandonedFiles();
	}
}
