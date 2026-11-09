package com.peergeek.enrollment;

import java.util.List;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearnerAccessMatrixIntegrationTest extends LearningTestSupport {

	interface EndpointRequestFactory {
		MockHttpServletRequestBuilder create(long courseId, long lessonId, long resourceId);
		boolean isModifying();
	}

	private static final List<EndpointRequestFactory> ALL_LEARNER_ENDPOINTS = List.of(
			new EndpointRequestFactory() {
				public MockHttpServletRequestBuilder create(long c, long l, long r) {
					return get("/api/v1/learning/courses/{courseId}", c);
				}
				public boolean isModifying() { return false; }
			},
			new EndpointRequestFactory() {
				public MockHttpServletRequestBuilder create(long c, long l, long r) {
					return get("/api/v1/learning/courses/{courseId}/lessons/{lessonId}", c, l);
				}
				public boolean isModifying() { return false; }
			},
			new EndpointRequestFactory() {
				public MockHttpServletRequestBuilder create(long c, long l, long r) {
					return get("/api/v1/learning/courses/{courseId}/continue", c);
				}
				public boolean isModifying() { return false; }
			},
			new EndpointRequestFactory() {
				public MockHttpServletRequestBuilder create(long c, long l, long r) {
					return post("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/visit", c, l);
				}
				public boolean isModifying() { return true; }
			},
			new EndpointRequestFactory() {
				public MockHttpServletRequestBuilder create(long c, long l, long r) {
					return put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", c, l)
							.contentType("application/json")
							.content("{\"completed\":true}");
				}
				public boolean isModifying() { return true; }
			},
			new EndpointRequestFactory() {
				public MockHttpServletRequestBuilder create(long c, long l, long r) {
					return get("/api/v1/learning/resources/{resourceId}/url", r);
				}
				public boolean isModifying() { return false; }
			}
	);

	@Test
	@DisplayName("Access matrix: anonymous requests return 401 across all endpoints")
	void accessMatrixAnonymousReturns401() throws Exception {
		UserEntity instructor = createInstructor("matrix1@example.test", "Instructor 1");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("Access matrix: signed-in not-enrolled on PUBLISHED course returns 403 ENROLLMENT_REQUIRED")
	void accessMatrixNotEnrolledPublishedReturns403() throws Exception {
		UserEntity instructor = createInstructor("matrix2@example.test", "Instructor 2");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity outsider = createUser("outsider2@example.test", "Outsider");
		String token = tokenFor(outsider);

		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			if (factory.create(0, 0, 0).buildRequest(null).getPathInfo() != null && factory.create(0, 0, 0).buildRequest(null).getPathInfo().contains("/resources/")) {
				// Resource URL endpoint returns 404 for invisible/denied resource to prevent existence leak
				mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
								.header("Authorization", bearer(token)))
						.andExpect(status().isNotFound());
			} else {
				mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
								.header("Authorization", bearer(token)))
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
			}
		}
	}

	@Test
	@DisplayName("Access matrix: not enrolled on unpublished, deleted, or nonexistent course returns 404 NOT_FOUND")
	void accessMatrixNotEnrolledUnpublishedReturns404() throws Exception {
		UserEntity instructor = createInstructor("matrix3@example.test", "Instructor 3");
		String instructorToken = tokenFor(instructor);

		// Unpublished
		CourseCreationResult unpub = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, unpub.courseId());
		unpublishCourse(instructorToken, unpub.courseId());
		long resUnpub = attachLessonResource(unpub.courseId(), unpub.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		// Deleted
		CourseCreationResult del = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		jdbc.update("update courses set deleted_at = now() where id = ?", del.courseId());
		long resDel = attachLessonResource(del.courseId(), del.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity outsider = createUser("outsider3@example.test", "Outsider 3");
		String token = tokenFor(outsider);

		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			// Unpublished course
			mockMvc.perform(factory.create(unpub.courseId(), unpub.lessonIds().getFirst(), resUnpub)
							.header("Authorization", bearer(token)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));

			// Deleted course
			mockMvc.perform(factory.create(del.courseId(), del.lessonIds().getFirst(), resDel)
							.header("Authorization", bearer(token)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));

			// Nonexistent course
			mockMvc.perform(factory.create(999999L, 888888L, 777777L)
							.header("Authorization", bearer(token)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}
	}

	@Test
	@DisplayName("Access matrix: ACTIVE learner has access (200 OK or 204 No Content)")
	void accessMatrixActiveLearnerHasAccess() throws Exception {
		UserEntity instructor = createInstructor("matrix4@example.test", "Instructor 4");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner4@example.test", "Learner 4");
		String token = tokenFor(learner);

		// Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token))).andExpect(status().isOk());

		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			ResultMatcher expectedStatus = factory.isModifying()
					? (factory.create(0, 0, 0).buildRequest(null).getPathInfo() != null && factory.create(0, 0, 0).buildRequest(null).getPathInfo().contains("/visit") ? status().isNoContent() : status().isOk())
					: status().isOk();

			mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
							.header("Authorization", bearer(token)))
					.andExpect(expectedStatus);
		}
	}

	@Test
	@DisplayName("Access matrix: enrolled learner keeps access after course is UNPUBLISHED")
	void accessMatrixEnrolledLearnerRetainsAccessAfterUnpublished() throws Exception {
		UserEntity instructor = createInstructor("matrix5@example.test", "Instructor 5");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner5@example.test", "Learner 5");
		String token = tokenFor(learner);

		// Enroll while published
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token))).andExpect(status().isOk());

		// Unpublish course
		unpublishCourse(instructorToken, course.courseId());

		// Enrolled learner still has full access!
		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			ResultMatcher expectedStatus = factory.isModifying()
					? (factory.create(0, 0, 0).buildRequest(null).getPathInfo() != null && factory.create(0, 0, 0).buildRequest(null).getPathInfo().contains("/visit") ? status().isNoContent() : status().isOk())
					: status().isOk();

			mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
							.header("Authorization", bearer(token)))
					.andExpect(expectedStatus);
		}
	}

	@Test
	@DisplayName("Access matrix: enrolled learner loses access (404) after soft-delete")
	void accessMatrixSoftDeleteReturns404EvenForEnrolled() throws Exception {
		UserEntity instructor = createInstructor("matrix6@example.test", "Instructor 6");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner6@example.test", "Learner 6");
		String token = tokenFor(learner);

		// Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token))).andExpect(status().isOk());

		// Soft delete course
		jdbc.update("update courses set deleted_at = now() where id = ?", course.courseId());

		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
							.header("Authorization", bearer(token)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}
	}

	@Test
	@DisplayName("Access matrix: WITHDRAWN learner gets 403 on published course")
	void accessMatrixWithdrawnLearnerReturns403() throws Exception {
		UserEntity instructor = createInstructor("matrix7@example.test", "Instructor 7");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long resourceId = attachLessonResource(course.courseId(), course.lessonIds().getFirst(), instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");

		UserEntity learner = createUser("learner7@example.test", "Learner 7");
		String token = tokenFor(learner);

		// Enroll and withdraw
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token))).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/learning/courses/{courseId}/enrollment", course.courseId())
				.header("Authorization", bearer(token))).andExpect(status().isNoContent());

		for (EndpointRequestFactory factory : ALL_LEARNER_ENDPOINTS) {
			if (factory.create(0, 0, 0).buildRequest(null).getPathInfo() != null && factory.create(0, 0, 0).buildRequest(null).getPathInfo().contains("/resources/")) {
				mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
								.header("Authorization", bearer(token)))
						.andExpect(status().isNotFound());
			} else {
				mockMvc.perform(factory.create(course.courseId(), course.lessonIds().getFirst(), resourceId)
								.header("Authorization", bearer(token)))
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
			}
		}
	}
}
