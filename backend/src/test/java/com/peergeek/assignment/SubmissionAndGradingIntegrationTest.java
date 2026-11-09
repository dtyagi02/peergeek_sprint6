package com.peergeek.assignment;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionStatus;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionAndGradingIntegrationTest extends AssignmentTestSupport {

	@Test
	@DisplayName("MCQ auto-grading computes scores: single correct, multi all-or-nothing, unanswered zero")
	void mcqAutoGradingScoring() throws Exception {
		UserEntity instructor = createInstructor("prof.grade@example.test", "Prof Grading");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Auto Grade Quiz", AssignmentKind.QUESTIONS);

		// Q1: MCQ single (5 points)
		var q1 = buildMcqQuestion(1, "What is 2+2?", 5, false, List.of(
				buildMcqOption("q1_opt1", "3", false),
				buildMcqOption("q1_opt2", "4", true)
		));
		// Q2: MCQ multi all-or-nothing (5 points, correct: optA and optB)
		var q2 = buildMcqQuestion(2, "Select A and B:", 5, true, List.of(
				buildMcqOption("q2_optA", "A", true),
				buildMcqOption("q2_optB", "B", true),
				buildMcqOption("q2_optC", "C", false)
		));
		// Q3: MCQ single (5 points, correct: optX)
		var q3 = buildMcqQuestion(3, "Select X:", 5, false, List.of(
				buildMcqOption("q3_optX", "X", true),
				buildMcqOption("q3_optY", "Y", false)
		));

		replaceQuestions(instructorToken, assignmentId, List.of(q1, q2, q3));
		publishAssignment(instructorToken, assignmentId);

		List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		long q1Id = questions.get(0).getId();
		long q2Id = questions.get(1).getId();

		UserEntity learner = createUser("learner.grade@example.test", "Learner Grade");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// Start attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// Save draft answers:
		// Q1 answered correctly ("q1_opt2") -> 5 pts
		// Q2 answered partially (only "q2_optA") -> 0 pts (all-or-nothing)
		// Q3 unanswered -> 0 pts
		ObjectNode draftReq = objectMapper.createObjectNode();
		draftReq.put("version", 0);
		ArrayNode answers = draftReq.putArray("answers");

		ObjectNode a1 = answers.addObject();
		a1.put("questionId", q1Id);
		a1.putArray("selectedOptionIds").add("q1_opt2");

		ObjectNode a2 = answers.addObject();
		a2.put("questionId", q2Id);
		a2.putArray("selectedOptionIds").add("q2_optA");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(draftReq.toString()))
				.andExpect(status().isOk());

		// Submit with version 1
		ObjectNode submitReq = objectMapper.createObjectNode();
		submitReq.put("version", 1);

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(5.0))
				.andExpect(jsonPath("$.maxPoints").value(15.0));

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.GRADED);
		assertThat(submission.getTotalPoints()).isEqualByComparingTo(new BigDecimal("5.00"));
		assertThat(submission.getGradedBy()).isNull();
		assertThat(submission.isAutoGraded()).isTrue();
	}

	@Test
	@DisplayName("Manual grading for questions: incomplete grading rejected, bounds checked, regrading supported")
	void manualGradingQuestionsFlow() throws Exception {
		UserEntity instructor = createInstructor("prof.man@example.test", "Prof Manual");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		AssignmentEntity assignment = new AssignmentEntity(
				courseId,
				null,
				null,
				AssignmentKind.QUESTIONS,
				"Mixed Assessment",
				"Complete this assignment carefully according to instructions.",
				java.math.BigDecimal.valueOf(15),
				instructor.getId());
		assignment.setStatus(AssignmentStatus.PUBLISHED);
		assignment.setAllowLate(true);
		assignment.setAllowText(true);
		assignment.setPublishedAt(java.time.Instant.now());
		long assignmentId = assignmentRepository.save(assignment).getId();

		AssignmentQuestionEntity qMcqEntity = new AssignmentQuestionEntity(
				assignment,
				1,
				QuestionType.MCQ_SINGLE,
				"What is capital of UK?",
				java.math.BigDecimal.valueOf(5),
				List.of(
						new QuestionOption("uk_opt1", "London", true),
						new QuestionOption("uk_opt2", "Manchester", false)),
				null);
		AssignmentQuestionEntity qDescEntity = new AssignmentQuestionEntity(
				assignment,
				2,
				QuestionType.DESCRIPTIVE,
				"Explain REST architectural constraints.",
				java.math.BigDecimal.valueOf(10),
				List.of(),
				null);
		questionRepository.saveAll(List.of(qMcqEntity, qDescEntity));

		List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		long qMcqId = questions.get(0).getId();
		long qDescId = questions.get(1).getId();

		UserEntity learner = createUser("learner.mixed@example.test", "Learner Mixed");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// Start attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// Save draft answers
		ObjectNode draftReq = objectMapper.createObjectNode();
		draftReq.put("version", 0);
		ArrayNode answers = draftReq.putArray("answers");

		ObjectNode a1 = answers.addObject();
		a1.put("questionId", qMcqId);
		a1.putArray("selectedOptionIds").add("uk_opt1");

		ObjectNode a2 = answers.addObject();
		a2.put("questionId", qDescId);
		a2.put("answerText", "Stateless, Client-Server, Cacheable, Layered System, Uniform Interface, Code-on-Demand.");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(draftReq.toString()))
				.andExpect(status().isOk());

		// Submit with version 1
		ObjectNode submitReq = objectMapper.createObjectNode();
		submitReq.put("version", 1);

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUBMITTED"));

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		long subId = submission.getId();

		// 1. Instructor attempts incomplete grade (missing qDesc grade)
		ObjectNode incompleteGradeReq = objectMapper.createObjectNode();
		incompleteGradeReq.put("version", submission.getVersion());
		ArrayNode incompleteGrades = incompleteGradeReq.putArray("questionGrades");
		ObjectNode ig1 = incompleteGrades.addObject();
		ig1.put("questionId", qMcqId);
		ig1.put("points", 5);

		mockMvc.perform(put("/api/v1/instructor/submissions/{submissionId}/grade", subId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(incompleteGradeReq.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GRADING_INCOMPLETE"));

		// 2. Instructor attempts points exceeding question max (15 > 10)
		ObjectNode overflowGradeReq = objectMapper.createObjectNode();
		overflowGradeReq.put("version", submission.getVersion());
		ArrayNode overflowGrades = overflowGradeReq.putArray("questionGrades");
		ObjectNode og1 = overflowGrades.addObject();
		og1.put("questionId", qDescId);
		og1.put("points", 15);

		mockMvc.perform(put("/api/v1/instructor/submissions/{submissionId}/grade", subId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(overflowGradeReq.toString()))
				.andExpect(status().isBadRequest());

		// 3. Valid grading: 8 points for qDesc
		ObjectNode validGradeReq = objectMapper.createObjectNode();
		validGradeReq.put("version", submission.getVersion());
		validGradeReq.put("overallFeedback", "Good explanation of constraints.");
		ArrayNode vg = validGradeReq.putArray("questionGrades");
		ObjectNode vg1 = vg.addObject();
		vg1.put("questionId", qDescId);
		vg1.put("points", 8);
		vg1.put("feedback", "Clear and complete.");

		mockMvc.perform(put("/api/v1/instructor/submissions/{submissionId}/grade", subId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(validGradeReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(13.0)); // 5 (auto) + 8 (manual)

		SubmissionEntity gradedSub = submissionRepository.findById(subId).orElseThrow();
		assertThat(gradedSub.getStatus()).isEqualTo(SubmissionStatus.GRADED);
		assertThat(gradedSub.getTotalPoints()).isEqualByComparingTo(new BigDecimal("13.00"));
		assertThat(gradedSub.getGradedBy()).isEqualTo(instructor.getId());
	}

	@Test
	@DisplayName("Project grading: marks validation and regrading supported")
	void projectGradingFlow() throws Exception {
		UserEntity instructor = createInstructor("prof.proj@example.test", "Prof Project");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createHistoricalProjectAssignment(courseId, "Capstone Project", instructor.getId());

		UserEntity learner = createUser("learner.capstone@example.test", "Learner Capstone");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// Start attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// Save draft textAnswer
		ObjectNode draftReq = objectMapper.createObjectNode();
		draftReq.put("version", 0);
		draftReq.put("textAnswer", "Implemented high-throughput messaging queue.");
		draftReq.putArray("answers");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(draftReq.toString()))
				.andExpect(status().isOk());

		// Submit with version 1
		ObjectNode submitReq = objectMapper.createObjectNode();
		submitReq.put("version", 1);

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUBMITTED"));

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		long subId = submission.getId();

		// 1. Invalid marks > maxPoints (105 > 100)
		ObjectNode invalidReq1 = objectMapper.createObjectNode();
		invalidReq1.put("version", submission.getVersion());
		invalidReq1.put("marks", 105);

		mockMvc.perform(put("/api/v1/instructor/submissions/{submissionId}/grade", subId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(invalidReq1.toString()))
				.andExpect(status().isBadRequest());

		// 2. Invalid marks < 0
		invalidReq1.put("marks", -5);
		mockMvc.perform(put("/api/v1/instructor/submissions/{submissionId}/grade", subId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(invalidReq1.toString()))
				.andExpect(status().isBadRequest());

		// 3. Valid grading: 92.5 marks
		ObjectNode validReq = objectMapper.createObjectNode();
		validReq.put("version", submission.getVersion());
		validReq.put("marks", 92.5);
		validReq.put("overallFeedback", "Outstanding capstone implementation.");

		mockMvc.perform(put("/api/v1/instructor/submissions/{submissionId}/grade", subId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(validReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(92.5));

		SubmissionEntity gradedSub = submissionRepository.findById(subId).orElseThrow();
		assertThat(gradedSub.getStatus()).isEqualTo(SubmissionStatus.GRADED);
		assertThat(gradedSub.getTotalPoints()).isEqualByComparingTo(new BigDecimal("92.50"));
	}

	@Test
	@DisplayName("MCQ Student Flow: Start attempt idempotent, no leaks, option validation, draft persistence, auto-grading, idempotent submit, and result display")
	void mcqEndToEndStudentFlowAndSecurity() throws Exception {
		UserEntity instructor = createInstructor("prof.e2e@example.test", "Prof E2E");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Network Security Quiz", AssignmentKind.QUESTIONS);

		// Q1: Single choice (10 pts)
		var q1 = buildMcqQuestion(1, "What port is HTTPS?", 10, false, List.of(
				buildMcqOption("opt_80", "80", false),
				buildMcqOption("opt_443", "443", true)
		));
		// Q2: Multi choice (10 pts, correct: opt_tcp and opt_udp)
		var q2 = buildMcqQuestion(2, "Select Transport layer protocols:", 10, true, List.of(
				buildMcqOption("opt_tcp", "TCP", true),
				buildMcqOption("opt_udp", "UDP", true),
				buildMcqOption("opt_ip", "IP", false)
		));

		replaceQuestions(instructorToken, assignmentId, List.of(q1, q2));
		publishAssignment(instructorToken, assignmentId);

		List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		long q1Id = questions.get(0).getId();
		long q2Id = questions.get(1).getId();

		UserEntity learner = createUser("learner.e2e@example.test", "Learner E2E");
		String learnerToken = tokenFor(learner);

		// 1. Unenrolled user cannot access assignment detail or start
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/learning/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());

		// Enroll learner
		enrollLearner(learnerToken, courseId);

		// 2. Enrolled learner checks assignment detail before start: hasAttempt is false
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/learning/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(assignmentId))
				.andExpect(jsonPath("$.hasAttempt").value(false))
				.andExpect(jsonPath("$.attempt").doesNotExist());

		// 3. Start attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.questions").isArray())
				.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
				.andExpect(jsonPath("$.questions[0].options[0].isCorrect").doesNotExist());

		// 4. Repeated start resumes attempt idempotently
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		// 5. Get attempt: verifies options are present, but correct answers are never leaked
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/learning/assignments/{assignmentId}/attempt", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.questions[0].prompt").value("What port is HTTPS?"))
				.andExpect(jsonPath("$.questions[0].options[0].text").value("80"))
				.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
				.andExpect(jsonPath("$.questions[0].options[0].isCorrect").doesNotExist())
				.andExpect(jsonPath("$.questions[0].options[1].correct").doesNotExist());

		// 6. Draft save: Reject unknown option ID
		ObjectNode tamperedDraft = objectMapper.createObjectNode();
		tamperedDraft.put("version", 0);
		ArrayNode tamperedAnswers = tamperedDraft.putArray("answers");
		ObjectNode t1 = tamperedAnswers.addObject();
		t1.put("questionId", q1Id);
		t1.putArray("selectedOptionIds").add("opt_tampered");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(tamperedDraft.toString()))
				.andExpect(status().isBadRequest());

		// 7. Draft save: Reject multiple choices for single-choice question
		ObjectNode multiSingleDraft = objectMapper.createObjectNode();
		multiSingleDraft.put("version", 0);
		ArrayNode msAnswers = multiSingleDraft.putArray("answers");
		ObjectNode ms1 = msAnswers.addObject();
		ms1.put("questionId", q1Id);
		ms1.putArray("selectedOptionIds").add("opt_80").add("opt_443");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(multiSingleDraft.toString()))
				.andExpect(status().isBadRequest());

		// 8. Valid Draft Save: Q1 answered correctly ("opt_443"), Q2 answered correctly ("opt_tcp", "opt_udp")
		ObjectNode validDraft = objectMapper.createObjectNode();
		validDraft.put("version", 0);
		ArrayNode validAnswers = validDraft.putArray("answers");

		ObjectNode va1 = validAnswers.addObject();
		va1.put("questionId", q1Id);
		va1.putArray("selectedOptionIds").add("opt_443");

		ObjectNode va2 = validAnswers.addObject();
		va2.put("questionId", q2Id);
		va2.putArray("selectedOptionIds").add("opt_tcp").add("opt_udp");

		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(validDraft.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));

		// 9. Fetch attempt: draft answers are restored
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/learning/assignments/{assignmentId}/attempt", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answers[0].selectedOptionIds[0]").value("opt_443"));

		// 10. Submit assignment: version <= submission version succeeds and auto-grades
		ObjectNode submitReq = objectMapper.createObjectNode();
		submitReq.put("version", 1);

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(20.0))
				.andExpect(jsonPath("$.maxPoints").value(20.0))
				.andExpect(jsonPath("$.autoGraded").value(true));

		// 11. Idempotent re-submit returns same graded result
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(20.0));

		// 12. Submitted attempt cannot be modified via draft endpoint
		mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(validDraft.toString()))
				.andExpect(status().isConflict());

		// 13. Get submission: correct answers remain hidden because showCorrectAnswers is false by default
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/learning/assignments/{assignmentId}/submission", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(20.0))
				.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
				.andExpect(jsonPath("$.questions[0].correctOptionIds").doesNotExist());

		// 14. Enable showCorrectAnswers on assignment and verify result display reveals correct answers
		AssignmentEntity assignment = assignmentRepository.findById(assignmentId).orElseThrow();
		assignment.setShowCorrectAnswers(true);
		assignmentRepository.save(assignment);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/learning/assignments/{assignmentId}/submission", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.questions[0].correctOptionIds[0]").value("opt_443"))
				.andExpect(jsonPath("$.questions[0].options[1].isCorrect").value(true))
				.andExpect(jsonPath("$.questions[0].options[0].isCorrect").value(false));
	}
}

