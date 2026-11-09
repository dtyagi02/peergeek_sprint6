package com.peergeek.assignment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssignmentQueryService {

	Page<AssignmentDeadlineSummary> listUpcomingDeadlines(Instant from, Instant to, Pageable pageable);

	Page<Long> listLearnerIdsWithoutSubmission(long assignmentId, Pageable pageable);

	int countPendingSubmissions(long courseId);

	List<AssignmentSummaryDto> getAssignmentSummaries(long courseId);
}
