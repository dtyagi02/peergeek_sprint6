package com.peergeek.assignment;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.peergeek.enrollment.EnrollmentQueryService;
import com.peergeek.submission.SubmissionRepository;
import com.peergeek.submission.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AssignmentQueryServiceImpl implements AssignmentQueryService {

	private final AssignmentRepository assignmentRepository;
	private final SubmissionRepository submissionRepository;
	private final EnrollmentQueryService enrollmentQueryService;

	public AssignmentQueryServiceImpl(
			AssignmentRepository assignmentRepository,
			SubmissionRepository submissionRepository,
			EnrollmentQueryService enrollmentQueryService) {
		this.assignmentRepository = assignmentRepository;
		this.submissionRepository = submissionRepository;
		this.enrollmentQueryService = enrollmentQueryService;
	}

	@Override
	public Page<AssignmentDeadlineSummary> listUpcomingDeadlines(Instant from, Instant to, Pageable pageable) {
		Instant effectiveFrom = from != null ? from : Instant.MIN;
		Instant effectiveTo = to != null ? to : Instant.MAX;
		Page<AssignmentEntity> page = assignmentRepository.findByStatusAndDueAtBetween(
				AssignmentStatus.PUBLISHED, effectiveFrom, effectiveTo, pageable);
		return page.map(a -> new AssignmentDeadlineSummary(
				a.getId(),
				a.getCourseId(),
				a.getTitle(),
				a.getDueAt(),
				a.getMaxPoints()
		));
	}

	@Override
	public Page<Long> listLearnerIdsWithoutSubmission(long assignmentId, Pageable pageable) {
		AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

		Set<Long> submittedUserIds = submissionRepository.findUserIdsByAssignmentIdAndStatusIn(
				assignmentId, Set.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED));

		Page<Long> activeLearnersPage = enrollmentQueryService.listActiveLearnerIds(
				assignment.getCourseId(), Pageable.unpaged());

		List<Long> unsubmitted = activeLearnersPage.getContent().stream()
				.filter(id -> !submittedUserIds.contains(id))
				.toList();

		int start = (int) pageable.getOffset();
		if (start >= unsubmitted.size()) {
			return new PageImpl<>(List.of(), pageable, unsubmitted.size());
		}
		int end = Math.min(start + pageable.getPageSize(), unsubmitted.size());
		return new PageImpl<>(unsubmitted.subList(start, end), pageable, unsubmitted.size());
	}

	@Override
	public int countPendingSubmissions(long courseId) {
		List<AssignmentEntity> assignments = assignmentRepository.findByCourseId(courseId);
		if (assignments.isEmpty()) {
			return 0;
		}
		List<Long> assignmentIds = assignments.stream().map(AssignmentEntity::getId).toList();
		return submissionRepository.countByAssignmentIdInAndStatus(assignmentIds, SubmissionStatus.SUBMITTED);
	}

	@Override
	public List<AssignmentSummaryDto> getAssignmentSummaries(long courseId) {
		return assignmentRepository.findByCourseId(courseId).stream()
				.map(a -> new AssignmentSummaryDto(
						a.getId(),
						a.getCourseId(),
						a.getTitle(),
						a.getKind(),
						a.getStatus(),
						a.getDueAt(),
						a.getMaxPoints()
				))
				.toList();
	}
}
