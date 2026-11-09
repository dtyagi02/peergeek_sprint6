package com.peergeek.assignment;

import java.util.List;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentAccessMatrixIntegrationTest extends AssignmentTestSupport {

	@Test
	@DisplayName("Instructor endpoints strictly enforce ownership: owner 200, other user 404, anonymous 401")
	void instructorAccessMatrix() throws Exception {
		UserEntity owner = createInstructor("owner@example.test", "Prof Owner");
		String ownerToken = tokenFor(owner);
		long courseId = createCourseWithModulesAndLessons(ownerToken, 1, 1);

		long assignmentId = createAssignment(ownerToken, courseId, "Access Assignment", AssignmentKind.QUESTIONS);

		UserEntity otherUser = createInstructor("other@example.test", "Prof Other");
		String otherToken = tokenFor(otherUser);

		// 1. Course assignment list
		// Anonymous -> 401
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/assignments", courseId))
				.andExpect(status().isUnauthorized());
		// Other instructor -> 404 (OwnershipGuard in 404 mode)
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		// Owner -> 200
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(ownerToken)))
				.andExpect(status().isOk());

		// 2. Assignment detail
		mockMvc.perform(get("/api/v1/instructor/assignments/{assignmentId}", assignmentId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/instructor/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(ownerToken)))
				.andExpect(status().isOk());

		// 3. Submissions queue
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/submissions", courseId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/submissions", courseId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/submissions", courseId)
						.header("Authorization", bearer(ownerToken)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Learner endpoints enforce enrollment and visibility: anonymous 401, unenrolled 403, draft 404, enrolled 200")
	void learnerAccessMatrix() throws Exception {
		UserEntity instructor = createInstructor("inst.mat@example.test", "Instructor Mat");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		// Create draft assignment
		long draftAssignmentId = createAssignment(instructorToken, courseId, "Draft Assignment", AssignmentKind.QUESTIONS);

		// Create published assignment
		long pubAssignmentId = createAssignment(instructorToken, courseId, "Published Assignment", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "Question?", 10, false, List.of(
				buildMcqOption("o1", "A", true),
				buildMcqOption("o2", "B", false)
		));
		replaceQuestions(instructorToken, pubAssignmentId, List.of(q));
		publishAssignment(instructorToken, pubAssignmentId);

		UserEntity enrolledLearner = createUser("enrolled@example.test", "Enrolled Learner");
		String enrolledToken = tokenFor(enrolledLearner);
		enrollLearner(enrolledToken, courseId);

		UserEntity unenrolledLearner = createUser("unenrolled@example.test", "Unenrolled Learner");
		String unenrolledToken = tokenFor(unenrolledLearner);

		// 1. Course assignment list
		// Anonymous -> 401
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/assignments", courseId))
				.andExpect(status().isUnauthorized());
		// Unenrolled -> 403
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(unenrolledToken)))
				.andExpect(status().isForbidden());
		// Enrolled -> 200, contains only PUBLISHED assignments
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(enrolledToken)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(pubAssignmentId));

		// 2. Draft assignment detail for learner -> 404
		mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}", draftAssignmentId)
						.header("Authorization", bearer(enrolledToken)))
				.andExpect(status().isNotFound());

		// 3. Published assignment detail for unenrolled -> 403
		mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}", pubAssignmentId)
						.header("Authorization", bearer(unenrolledToken)))
				.andExpect(status().isForbidden());

		// 4. Published assignment detail for enrolled -> 200
		mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}", pubAssignmentId)
						.header("Authorization", bearer(enrolledToken)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.id").value(pubAssignmentId));
	}

	@Test
	@DisplayName("Learner A cannot access Learner B's active attempt, draft, or submission results")
	void crossLearnerIsolation() throws Exception {
		UserEntity instructor = createInstructor("inst.iso@example.test", "Instructor Isolation");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Iso Assignment", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "Question?", 10, false, List.of(
				buildMcqOption("o1", "A", true),
				buildMcqOption("o2", "B", false)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));
		publishAssignment(instructorToken, assignmentId);

		UserEntity learnerA = createUser("learnerA@example.test", "Learner Alpha");
		String tokenA = tokenFor(learnerA);
		enrollLearner(tokenA, courseId);

		UserEntity learnerB = createUser("learnerB@example.test", "Learner Beta");
		String tokenB = tokenFor(learnerB);
		enrollLearner(tokenB, courseId);

		// Learner A starts attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(tokenA)))
				.andExpect(status().isOk());

		// Learner B accessing attempt -> returns 404 because Learner B has not started their attempt yet!
		mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}/attempt", assignmentId)
						.header("Authorization", bearer(tokenB)))
				.andExpect(status().isNotFound());
	}
}
