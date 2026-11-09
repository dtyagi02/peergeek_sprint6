package com.peergeek.submission;

public interface SubmissionQueryService {

	AssignmentSubmissionAggregates getSubmissionAggregates(long assignmentId);
}
