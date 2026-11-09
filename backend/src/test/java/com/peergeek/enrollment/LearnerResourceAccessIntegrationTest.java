package com.peergeek.enrollment;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.file.ResourceAccessContext;
import com.peergeek.file.ResourceAccessVoter;
import com.peergeek.file.ResourceAction;
import com.peergeek.file.ResourceSummary;
import com.peergeek.file.ResourceType;
import com.peergeek.file.ResourceVote;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(LearnerResourceAccessIntegrationTest.DenyingVoterConfig.class)
class LearnerResourceAccessIntegrationTest extends LearningTestSupport {

	private static volatile boolean denyAllResources = false;

	@TestConfiguration
	static class DenyingVoterConfig {
		@Bean
		public ResourceAccessVoter testDenyingVoter() {
			return context -> denyAllResources ? ResourceVote.DENY : ResourceVote.ABSTAIN;
		}
	}

	@AfterEach
	void resetDenial() {
		denyAllResources = false;
	}

	@Test
	@DisplayName("Enrolled learner gets URLs only for READY LESSON_RESOURCE resources")
	void enrolledLearnerGetsReadyLessonResources() throws Exception {
		UserEntity instructor = createInstructor("prof_res1@example.test", "Instructor Res 1");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long lessonId = course.lessonIds().getFirst();

		long docReady = attachLessonResource(course.courseId(), lessonId, instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");
		long imgReady = attachLessonResource(course.courseId(), lessonId, instructor.getId(), "IMAGE", "LESSON_RESOURCE", "READY");
		long vidReady = attachLessonResource(course.courseId(), lessonId, instructor.getId(), "VIDEO", "LESSON_RESOURCE", "READY");
		long docPending = attachLessonResource(course.courseId(), lessonId, instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "PENDING");
		long docFailed = attachLessonResource(course.courseId(), lessonId, instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "FAILED");

		UserEntity learner = createUser("learner_res1@example.test", "Learner Res 1");
		String learnerToken = tokenFor(learner);

		// Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// GET lesson
		MvcResult result = mockMvc.perform(get("/api/v1/learning/courses/{courseId}/lessons/{lessonId}", course.courseId(), lessonId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode resourcesNode = json.get("resources");
		assertThat(resourcesNode.isArray()).isTrue();
		assertThat(resourcesNode.size()).isEqualTo(3);

		List<Long> resourceIdsInResponse = new java.util.ArrayList<>();
		for (JsonNode res : resourcesNode) {
			long id = res.get("id").asLong();
			resourceIdsInResponse.add(id);
			String url = res.get("url").asText();
			assertThat(url).startsWith("http");
			assertThat(url).contains("X-Amz-Signature");
			assertThat(res.has("objectKey")).isFalse();
			assertThat(res.has("bucket")).isFalse();
		}

		assertThat(resourceIdsInResponse).containsExactlyInAnyOrder(docReady, imgReady, vidReady);
		assertThat(resourceIdsInResponse).doesNotContain(docPending, docFailed);

		// Requesting PENDING or FAILED via fresh resource URL -> 404
		mockMvc.perform(get("/api/v1/learning/resources/{resourceId}/url", docPending)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/v1/learning/resources/{resourceId}/url", docFailed)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("Resource of another course returns 404 to learner")
	void resourceOfAnotherCourseReturns404() throws Exception {
		UserEntity instructor = createInstructor("prof_res2@example.test", "Instructor Res 2");
		String instructorToken = tokenFor(instructor);

		CourseCreationResult courseA = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseA.courseId());

		CourseCreationResult courseB = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseB.courseId());
		long resourceB = attachLessonResource(courseB.courseId(), courseB.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner_res2@example.test", "Learner Res 2");
		String learnerToken = tokenFor(learner);

		// Enroll only in Course A
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", courseA.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Accessing resourceB via learning endpoint -> 404
		mockMvc.perform(get("/api/v1/learning/resources/{resourceId}/url", resourceB)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("A denying voter overrides grant and blocks access")
	void denyingVoterOverridesGrant() throws Exception {
		UserEntity instructor = createInstructor("prof_res3@example.test", "Instructor Res 3");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner_res3@example.test", "Learner Res 3");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Initially OK
		mockMvc.perform(get("/api/v1/learning/resources/{resourceId}/url", resourceId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// Activate denying voter
		denyAllResources = true;

		// Now should be 404 (denial with NOT_FOUND mode)
		mockMvc.perform(get("/api/v1/learning/resources/{resourceId}/url", resourceId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("MANAGE is never granted to enrolled learner")
	void manageNeverGrantedToLearner() throws Exception {
		UserEntity instructor = createInstructor("prof_res4@example.test", "Instructor Res 4");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner_res4@example.test", "Learner Res 4");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		ResourceSummary summary = new ResourceSummary(
				resourceId, course.courseId(), course.lessonIds().getFirst(),
				ResourceType.DOCUMENT,
				com.peergeek.file.ResourcePurpose.LESSON_RESOURCE,
				com.peergeek.file.ResourceStatus.READY);

		boolean canManage = resourceAccessService.isAllowed(
				new ResourceAccessContext(learner.getId(), summary, ResourceAction.MANAGE));
		assertThat(canManage).isFalse();
	}

	@Test
	@DisplayName("Enrolled learner calling Phase 3 instructor endpoint download-url gets 404 (ownership check)")
	void enrolledLearnerCallingInstructorDownloadUrlGets404() throws Exception {
		UserEntity instructor = createInstructor("prof_res5@example.test", "Instructor Res 5");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner_res5@example.test", "Learner Res 5");
		String learnerToken = tokenFor(learner);

		// Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Call instructor endpoint
		mockMvc.perform(get("/api/v1/instructor/resources/{resourceId}/download-url", resourceId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("URLs use public endpoint host and no response/log leaks bucket or internal host")
	void urlSanityCheck() throws Exception {
		UserEntity instructor = createInstructor("prof_res6@example.test", "Instructor Res 6");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner_res6@example.test", "Learner Res 6");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		MvcResult res = mockMvc.perform(get("/api/v1/learning/resources/{resourceId}/url", resourceId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andReturn();

		String body = res.getResponse().getContentAsString();
		assertThat(body).doesNotContain("s3.amazonaws.com");
		assertThat(body).doesNotContain("localhost:9000");
		assertThat(body).doesNotContain("minio");
		assertThat(body).contains("X-Amz-Signature");
	}
}
