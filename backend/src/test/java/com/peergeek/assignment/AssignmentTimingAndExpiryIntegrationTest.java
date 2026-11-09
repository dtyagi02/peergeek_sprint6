package com.peergeek.assignment;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionStatus;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentTimingAndExpiryIntegrationTest extends AssignmentTestSupport {

	@Autowired private AssignmentExpiryJob expiryJob;

	@Test
	@DisplayName("Start window checks opensAt in the future and dueAt in the past")
	void startWindowChecks() throws Exception {
		UserEntity instructor = createInstructor("prof.time@example.test", "Prof Timing");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Window Test", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "Question?", 10, false, List.of(
				buildMcqOption("o1", "A", true),
				buildMcqOption("o2", "B", false)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));

		// Set opensAt in the future
		long verFuture = assignmentRepository.findById(assignmentId).orElseThrow().getVersion();
		ObjectNode patchFuture = objectMapper.createObjectNode();
		patchFuture.put("version", verFuture);
		patchFuture.put("opensAt", Instant.now().plus(2, ChronoUnit.HOURS).toString());
		patchFuture.put("allowLate", false);
		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(patchFuture.toString()))
				.andExpect(status().isOk());

		publishAssignment(instructorToken, assignmentId);

		UserEntity learner = createUser("learner.time@example.test", "Learner Time");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// Attempting to start before opensAt fails with 409 ASSIGNMENT_NOT_OPEN
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_OPEN"));

		// Unpublish and change to opensAt in past, dueAt in past without allowLate
		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/unpublish", assignmentId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());

		long verPast = assignmentRepository.findById(assignmentId).orElseThrow().getVersion();
		ObjectNode patchPast = objectMapper.createObjectNode();
		patchPast.put("version", verPast);
		patchPast.put("opensAt", Instant.now().minus(2, ChronoUnit.HOURS).toString());
		patchPast.put("dueAt", Instant.now().minus(1, ChronoUnit.HOURS).toString());
		patchPast.put("allowLate", false);
		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(patchPast.toString()))
				.andExpect(status().isOk());

		publishAssignment(instructorToken, assignmentId);

		// Attempting to start after dueAt without allowLate fails with 409 DEADLINE_PASSED
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));

		// Now enable allowLate
		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/unpublish", assignmentId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());

		long verLate = assignmentRepository.findById(assignmentId).orElseThrow().getVersion();
		ObjectNode patchLate = objectMapper.createObjectNode();
		patchLate.put("version", verLate);
		patchLate.put("allowLate", true);
		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(patchLate.toString()))
				.andExpect(status().isOk());

		publishAssignment(instructorToken, assignmentId);

		// Now starting succeeds and sets late = true
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		SubmissionEntity lateSub = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		assertThat(lateSub.isLate()).isTrue();
	}

	@Test
	@DisplayName("Idempotent start returns identical deadline; expiry finalizes in-progress attempt")
	void idempotentStartAndExpiryJob() throws Exception {
		UserEntity instructor = createInstructor("prof.exp@example.test", "Prof Expiry");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Timed Expiry", AssignmentKind.QUESTIONS);
		ObjectNode patchReq = objectMapper.createObjectNode();
		patchReq.put("version", 0);
		patchReq.put("timeLimitMinutes", 30);
		patchReq.put("allowLate", true);
		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(patchReq.toString()))
				.andExpect(status().isOk());

		var q = buildMcqQuestion(1, "What is capital of France?", 5, false, List.of(
				buildMcqOption("o1", "Paris", true),
				buildMcqOption("o2", "Lyon", false)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));
		publishAssignment(instructorToken, assignmentId);

		UserEntity learner = createUser("learner.exp@example.test", "Learner Expiry");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// 1. First start
		var start1 = mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andReturn();
		String deadline1 = objectMapper.readTree(start1.getResponse().getContentAsString()).get("deadlineAt").asText();

		// 2. Second start returns identical deadline
		var start2 = mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andReturn();
		String deadline2 = objectMapper.readTree(start2.getResponse().getContentAsString()).get("deadlineAt").asText();
		assertThat(deadline1).isEqualTo(deadline2);

		// 3. Save a draft answer
		long qId = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId).get(0).getId();
		ObjectNode draftReq = objectMapper.createObjectNode();
		draftReq.put("version", 0);
		var answers = draftReq.putArray("answers");
		var a = answers.addObject();
		a.put("questionId", qId);
		a.putArray("selectedOptionIds").add("o1");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(draftReq.toString()))
				.andExpect(status().isOk());

		// 4. Fast forward deadline in DB to simulate expired attempt past grace period
		SubmissionEntity subToExpire = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		subToExpire.setDeadlineAt(clock.instant().minus(Duration.ofSeconds(30)));
		submissionRepository.saveAndFlush(subToExpire);

		// 5. Expiry job runs
		expiryJob.run();

		// Submission should now be auto-finalized (and since MCQ is auto-graded, it becomes GRADED!)
		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		assertThat(submission.getStatus()).isIn(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED);
		assertThat(submission.isAutoSubmitted()).isTrue();
	}
}
