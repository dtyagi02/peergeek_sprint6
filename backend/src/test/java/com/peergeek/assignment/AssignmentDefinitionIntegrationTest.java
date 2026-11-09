package com.peergeek.assignment;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentDefinitionIntegrationTest extends AssignmentTestSupport {

	@Test
	@DisplayName("Instructor can create, patch, replace questions, publish, unpublish, and delete an assignment")
	void fullLifecycleAssignmentDefinition() throws Exception {
		UserEntity instructor = createInstructor("prof.def@example.test", "Prof Definition");
		String token = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(token, 1, 1);

		// 1. Create assignment
		ObjectNode createReq = objectMapper.createObjectNode();
		createReq.put("title", "Midterm Assessment");
		createReq.put("kind", "QUESTIONS");
		createReq.put("instructions", "Midterm covering chapters 1 through 3.");
		createReq.put("timeLimitMinutes", 60);
		createReq.put("allowLate", false);
		createReq.put("allowText", true);

		var createRes = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(createReq.toString()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.timeLimitMinutes").value(60))
				.andReturn();

		long assignmentId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

		// 2. Patch assignment metadata
		ObjectNode patchReq = objectMapper.createObjectNode();
		patchReq.put("version", 0);
		patchReq.put("title", "Midterm Assessment (Updated)");
		patchReq.put("instructions", "Updated instructions for midterm exam.");
		patchReq.put("timeLimitMinutes", 90);

		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(patchReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Midterm Assessment (Updated)"))
				.andExpect(jsonPath("$.timeLimitMinutes").value(90));

		// 3. Attempting to publish without questions fails with validation error
		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/publish", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'questions')]").exists());

		// 4. Replace questions with valid MCQ and Project questions
		var q1 = buildMcqQuestion(1, "What is the complexity of binary search?", 5, false, List.of(
				buildMcqOption("opt1", "O(1)", false),
				buildMcqOption("opt2", "O(log n)", true),
				buildMcqOption("opt3", "O(n)", false)
		));
		var q2 = buildMcqQuestion(2, "Select all idempotent HTTP methods:", 5, true, List.of(
				buildMcqOption("optA", "GET", true),
				buildMcqOption("optB", "POST", false),
				buildMcqOption("optC", "PUT", true),
				buildMcqOption("optD", "DELETE", true)
		));
		replaceQuestions(token, assignmentId, List.of(q1, q2));

		// Verify questions saved
		mockMvc.perform(get("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maxPoints").value(10))
				.andExpect(jsonPath("$.questions.length()").value(2));

		// 5. Publish assignment
		publishAssignment(token, assignmentId);

		mockMvc.perform(get("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));

		// Verify audit log
		Integer publishAudits = jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'ASSIGNMENT_PUBLISHED' and entity_id = ?",
				Integer.class, assignmentId);
		assertThat(publishAudits).isEqualTo(1);

		// 6. Unpublish assignment
		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/unpublish", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNPUBLISHED"));

		// 7. Delete assignment
		mockMvc.perform(delete("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());

		// Ensure 404 after deletion
		mockMvc.perform(get("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Publish validation rejects invalid question combinations with fieldErrors")
	void publishValidationFailures() throws Exception {
		UserEntity instructor = createInstructor("prof.val@example.test", "Prof Validation");
		String token = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(token, 1, 1);

		long assignmentId = createAssignment(token, courseId, "Faulty Assignment", AssignmentKind.QUESTIONS);

		// Case A: MCQ single without any correct option
		var faultyMcq = buildMcqQuestion(1, "No correct answers here", 10, false, List.of(
				buildMcqOption("o1", "Alpha", false),
				buildMcqOption("o2", "Beta", false)
		));
		replaceQuestions(token, assignmentId, List.of(faultyMcq));

		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/publish", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'questions[0].options')]").exists());

		// Case B: MCQ single with multiple correct options
		var multiCorrectMcq = buildMcqQuestion(1, "Two correct in single choice", 10, false, List.of(
				buildMcqOption("o1", "Alpha", true),
				buildMcqOption("o2", "Beta", true)
		));
		replaceQuestions(token, assignmentId, List.of(multiCorrectMcq));

		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/publish", assignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'questions[0].options')]").exists());

		// Case C: opensAt after dueAt is rejected on update
		ObjectNode datePatch = objectMapper.createObjectNode();
		long verDate = assignmentRepository.findById(assignmentId).orElseThrow().getVersion();
		datePatch.put("version", verDate);
		datePatch.put("opensAt", Instant.now().plusSeconds(86400).toString());
		datePatch.put("dueAt", Instant.now().plusSeconds(3600).toString());
		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(datePatch.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'opensAt')]").exists());

		// Case D: publishing an assignment with no questions fails
		long emptyAssignmentId = createAssignment(token, courseId, "Empty Questions Assignment", AssignmentKind.QUESTIONS);
		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/publish", emptyAssignmentId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'questions')]").exists());
	}

	@Test
	@DisplayName("Assignment questions cannot be replaced or deleted once an attempt has started")
	void questionsLockedAfterAttemptStarts() throws Exception {
		UserEntity instructor = createInstructor("prof.lock@example.test", "Prof Lock");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Lock Test", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "What is 2+2?", 5, false, List.of(
				buildMcqOption("o1", "3", false),
				buildMcqOption("o2", "4", true)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));
		publishAssignment(instructorToken, assignmentId);

		UserEntity learner = createUser("learner.lock@example.test", "Learner Lock");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// Learner starts attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// 1. Instructor replacing questions is rejected with 409 ASSIGNMENT_LOCKED
		ArrayNode arr = objectMapper.createArrayNode();
		arr.add(buildMcqQuestion(1, "Modified prompt?", 5, false, List.of(
				buildMcqOption("o1", "Yes", true),
				buildMcqOption("o2", "No", false)
		)));

		mockMvc.perform(put("/api/v1/instructor/assignments/{assignmentId}/questions", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(arr.toString()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ASSIGNMENT_LOCKED"));

		// 2. Instructor deleting assignment is rejected with 409 ASSIGNMENT_LOCKED
		mockMvc.perform(delete("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ASSIGNMENT_LOCKED"));

		// 3. Updating assignment title or instructions is still permitted!
		ObjectNode patch = objectMapper.createObjectNode();
		long currentVersion = assignmentRepository.findById(assignmentId).orElseThrow().getVersion();
		patch.put("version", currentVersion);
		patch.put("title", "Lock Test (Updated Title)");
		mockMvc.perform(patch("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(patch.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Lock Test (Updated Title)"));
	}

	@Test
	@DisplayName("Creating non-QUESTIONS assignment is rejected by instructor workflow")
	void rejectProjectAssignmentCreation() throws Exception {
		UserEntity instructor = createInstructor("prof.reject@example.test", "Prof Reject");
		String token = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(token, 1, 1);

		ObjectNode createReq = objectMapper.createObjectNode();
		createReq.put("title", "Project Not Allowed");
		createReq.put("kind", "PROJECT");
		createReq.put("instructions", "Instructions.");
		createReq.put("maxPoints", 100);

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(createReq.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'kind')]").exists());
	}

	@Test
	@DisplayName("Adding non-MCQ questions (DESCRIPTIVE, CODE) is rejected by instructor workflow")
	void rejectNonMcqQuestionTypes() throws Exception {
		UserEntity instructor = createInstructor("prof.mcqonly@example.test", "Prof MCQ Only");
		String token = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(token, 1, 1);

		long assignmentId = createAssignment(token, courseId, "MCQ Only Test", AssignmentKind.QUESTIONS);

		// Case A: DESCRIPTIVE question
		ObjectNode descQ = objectMapper.createObjectNode();
		descQ.put("type", "DESCRIPTIVE");
		descQ.put("prompt", "Write an essay");
		descQ.put("points", 10);

		ArrayNode arr = objectMapper.createArrayNode();
		arr.add(descQ);

		mockMvc.perform(put("/api/v1/instructor/assignments/{assignmentId}/questions", assignmentId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(arr.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'questions[0].type')]").exists());

		// Case B: CODE question
		ObjectNode codeQ = objectMapper.createObjectNode();
		codeQ.put("type", "CODE");
		codeQ.put("prompt", "Write code");
		codeQ.put("points", 10);
		codeQ.put("codeLanguage", "python");

		ArrayNode arrCode = objectMapper.createArrayNode();
		arrCode.add(codeQ);

		mockMvc.perform(put("/api/v1/instructor/assignments/{assignmentId}/questions", assignmentId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(arrCode.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'questions[0].type')]").exists());
	}
}
