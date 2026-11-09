package com.peergeek.assignment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.readiness.ReadinessContext;
import com.peergeek.readiness.ReadinessIssue;
import com.peergeek.readiness.ReadinessRule;
import com.peergeek.submission.AssignmentSubmissionAggregates;
import com.peergeek.submission.SubmissionQueryService;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentReadinessRuleAndQueryServiceTest extends AssignmentTestSupport {

	@Autowired private AssignmentDraftExistsReadinessRule readinessRule;
	@Autowired private AssignmentQueryService assignmentQueryService;
	@Autowired private SubmissionQueryService submissionQueryService;

	@Test
	@DisplayName("AssignmentDraftExistsReadinessRule flags draft assignments as WARNING and never BLOCKING_ERROR")
	void readinessRuleFlagsDraftsAsWarningOnly() throws Exception {
		UserEntity instructor = createInstructor("prof.rr@example.test", "Prof RR");
		String token = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(token, 1, 1);

		CourseMetadataSnapshot metadata = new CourseMetadataSnapshot(
				courseId, instructor.getId(), CourseStatus.DRAFT, false, 1,
				"Computer Systems", "A description", getDefaultCategoryId(),
				"Tech", true, CourseDifficulty.BEGINNER, List.of("systems"), null);
		var structure = new CourseStructureQueryService.CourseStructure(
				new CourseStructureQueryService.CourseSummary(courseId, instructor.getId(), "Title", "Desc",
						CourseDifficulty.BEGINNER, CourseStatus.DRAFT, 1), List.of());
		ReadinessContext context = new ReadinessContext(metadata, structure, List.of());

		// 1. No assignments -> no issues
		List<ReadinessIssue> issuesBefore = readinessRule.evaluate(context);
		assertThat(issuesBefore).isEmpty();

		// 2. Add a DRAFT assignment -> flags WARNING
		long assignmentId = createAssignment(token, courseId, "Draft Assignment", AssignmentKind.QUESTIONS);
		List<ReadinessIssue> issuesAfter = readinessRule.evaluate(context);
		assertThat(issuesAfter).hasSize(1);
		ReadinessIssue issue = issuesAfter.get(0);
		assertThat(issue.ruleCode()).isEqualTo("ASSIGNMENT_DRAFT_EXISTS");
		assertThat(issue.severity()).isEqualTo(ReadinessIssue.Severity.WARNING);
		assertThat(issue.scope()).isEqualTo(ReadinessIssue.Scope.COURSE);
		assertThat(issue.severity()).isNotEqualTo(ReadinessIssue.Severity.BLOCKING_ERROR);

		// 3. Publish assignment -> no draft exists -> issues become empty
		var q = buildMcqQuestion(1, "Q?", 5, false, List.of(
				buildMcqOption("o1", "A", true),
				buildMcqOption("o2", "B", false)
		));
		replaceQuestions(token, assignmentId, List.of(q));
		publishAssignment(token, assignmentId);

		List<ReadinessIssue> issuesPublished = readinessRule.evaluate(context);
		assertThat(issuesPublished).isEmpty();
	}

	@Test
	@DisplayName("AssignmentQueryService and SubmissionQueryService provide accurate read-only aggregates for Phases 9 and 10")
	void queryServicesAggregates() throws Exception {
		UserEntity instructor = createInstructor("prof.qs@example.test", "Prof QS");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Aggregates Test", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "What is 2+2?", 10, false, List.of(
				buildMcqOption("o1", "3", false),
				buildMcqOption("o2", "4", true)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));
		publishAssignment(instructorToken, assignmentId);

		// Update due date to within 24h
		jdbc.update("update assignments set due_at = ? where id = ?",
				Instant.now().plus(12, ChronoUnit.HOURS), assignmentId);

		UserEntity learner1 = createUser("l1@example.test", "Learner 1");
		String token1 = tokenFor(learner1);
		enrollLearner(token1, courseId);

		UserEntity learner2 = createUser("l2@example.test", "Learner 2");
		String token2 = tokenFor(learner2);
		enrollLearner(token2, courseId);

		// Learner 1 starts and submits (scored 10/10)
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(token1)))
				.andExpect(status().isOk());

		long qId = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId).get(0).getId();
		var draftReq = objectMapper.createObjectNode();
		draftReq.put("version", 0);
		var answers = draftReq.putArray("answers");
		var a1 = answers.addObject();
		a1.put("questionId", qId);
		a1.putArray("selectedOptionIds").add("o2");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(token1))
						.contentType("application/json")
						.content(draftReq.toString()))
				.andExpect(status().isOk());

		var submitReq = objectMapper.createObjectNode();
		submitReq.put("version", 1);

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(token1))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk());

		// Learner 2 has NOT submitted

		// 1. listUpcomingDeadlines
		Page<AssignmentDeadlineSummary> upcoming = assignmentQueryService.listUpcomingDeadlines(
				Instant.now().minus(1, ChronoUnit.HOURS),
				Instant.now().plus(24, ChronoUnit.HOURS),
				PageRequest.of(0, 10));
		assertThat(upcoming.getContent()).extracting(AssignmentDeadlineSummary::assignmentId)
				.contains(assignmentId);

		// 2. listLearnerIdsWithoutSubmission: learner2 should be in this list, but not learner1
		Page<Long> withoutSub = assignmentQueryService.listLearnerIdsWithoutSubmission(
				assignmentId, PageRequest.of(0, 10));
		assertThat(withoutSub.getContent()).contains(learner2.getId());
		assertThat(withoutSub.getContent()).doesNotContain(learner1.getId());

		// 3. countPendingSubmissions (for learner1 who auto-graded, pending is 0)
		int pending = assignmentQueryService.countPendingSubmissions(courseId);
		assertThat(pending).isEqualTo(0);

		// 4. getAssignmentSummaries
		List<AssignmentSummaryDto> summaries = assignmentQueryService.getAssignmentSummaries(courseId);
		assertThat(summaries).extracting(AssignmentSummaryDto::id).contains(assignmentId);

		// 5. SubmissionQueryService aggregates
		AssignmentSubmissionAggregates agg = submissionQueryService.getSubmissionAggregates(assignmentId);
		assertThat(agg.assignmentId()).isEqualTo(assignmentId);
		assertThat(agg.totalAttempts()).isEqualTo(1);
		assertThat(agg.submittedCount()).isEqualTo(1);
		assertThat(agg.gradedCount()).isEqualTo(1);
		assertThat(agg.averageScore()).isEqualTo(10.0);
		assertThat(agg.maxScore()).isEqualTo(10);
		assertThat(agg.minScore()).isEqualTo(10);
	}
}
