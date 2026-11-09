package com.peergeek.submission;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubmissionQueryServiceImpl implements SubmissionQueryService {

	private final SubmissionRepository submissionRepository;

	public SubmissionQueryServiceImpl(SubmissionRepository submissionRepository) {
		this.submissionRepository = submissionRepository;
	}

	@Override
	public AssignmentSubmissionAggregates getSubmissionAggregates(long assignmentId) {
		long totalAttempts = submissionRepository.countByAssignmentId(assignmentId);
		long submittedCount = submissionRepository.countByAssignmentIdAndStatusIn(
				assignmentId, List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED));
		long gradedCount = submissionRepository.countByAssignmentIdAndStatus(
				assignmentId, SubmissionStatus.GRADED);

		SubmissionRepository.SubmissionScoreStats stats = submissionRepository.getScoreStatsByAssignmentId(
				assignmentId, SubmissionStatus.GRADED);

		Double avgScore = (stats != null && stats.getAvgScore() != null) ? stats.getAvgScore() : null;
		Integer maxScore = (stats != null && stats.getMaxScore() != null) ? stats.getMaxScore() : null;
		Integer minScore = (stats != null && stats.getMinScore() != null) ? stats.getMinScore() : null;

		return new AssignmentSubmissionAggregates(
				assignmentId,
				totalAttempts,
				submittedCount,
				gradedCount,
				avgScore,
				maxScore,
				minScore
		);
	}
}
