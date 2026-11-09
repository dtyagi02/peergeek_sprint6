package com.peergeek.assignment;

import java.util.List;
import com.peergeek.readiness.ReadinessContext;
import com.peergeek.readiness.ReadinessIssue;
import com.peergeek.readiness.ReadinessRule;
import org.springframework.stereotype.Component;

@Component
public class AssignmentDraftExistsReadinessRule implements ReadinessRule {

	private final AssignmentRepository assignmentRepository;

	public AssignmentDraftExistsReadinessRule(AssignmentRepository assignmentRepository) {
		this.assignmentRepository = assignmentRepository;
	}

	@Override
	public String code() {
		return "ASSIGNMENT_DRAFT_EXISTS";
	}

	@Override
	public RulePhase phase() {
		return RulePhase.FAST;
	}

	@Override
	public List<ReadinessIssue> evaluate(ReadinessContext context) {
		if (context == null || context.metadata() == null) {
			return List.of();
		}
		long courseId = context.metadata().courseId();
		if (assignmentRepository.existsByCourseIdAndStatus(courseId, AssignmentStatus.DRAFT)) {
			return List.of(new ReadinessIssue(
					"ASSIGNMENT_DRAFT_EXISTS",
					ReadinessIssue.Severity.WARNING,
					ReadinessIssue.Scope.COURSE,
					null,
					null,
					null,
					null,
					null,
					null,
					"The course has assignments still in DRAFT status.",
					"Review and publish or remove draft assignments before publishing the course.",
					null
			));
		}
		return List.of();
	}
}
