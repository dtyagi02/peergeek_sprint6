package com.peergeek.bookmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.enrollment.LearningTestSupport;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookmarkIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Learner can bookmark, verify status, list, and remove course bookmarks")
	void courseBookmarkLifecycle() throws Exception {
		UserEntity instructor = createInstructor("prof.bmk@test.local", "Prof Bookmark");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student = createUser("student.bmk@test.local", "Student Bookmark");
		String studentToken = tokenFor(student);

		// Initially not bookmarked
		mockMvc.perform(get("/api/v1/courses/{courseId}/bookmarks/status", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookmarked").value(false));

		// Bookmark course
		mockMvc.perform(post("/api/v1/courses/{courseId}/bookmarks", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetType").value("COURSE"))
				.andExpect(jsonPath("$.courseId").value(course.courseId()));

		// Repeated bookmark is idempotent
		mockMvc.perform(post("/api/v1/courses/{courseId}/bookmarks", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		// DB has exactly 1 row
		Integer dbCount = jdbc.queryForObject(
				"select count(*) from bookmarks where user_id = ? and course_id = ?",
				Integer.class, student.getId(), course.courseId());
		assertThat(dbCount).isEqualTo(1);

		// Status is now bookmarked
		mockMvc.perform(get("/api/v1/courses/{courseId}/bookmarks/status", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookmarked").value(true));

		// List bookmarks
		mockMvc.perform(get("/api/v1/bookmarks")
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].targetType").value("COURSE"))
				.andExpect(jsonPath("$[0].courseId").value(course.courseId()));

		// Remove course bookmark
		mockMvc.perform(delete("/api/v1/courses/{courseId}/bookmarks", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNoContent());

		// Status is false
		mockMvc.perform(get("/api/v1/courses/{courseId}/bookmarks/status", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookmarked").value(false));
	}

	@Test
	@DisplayName("Learner can bookmark lesson and delete by bookmark id")
	void lessonBookmarkLifecycle() throws Exception {
		UserEntity instructor = createInstructor("prof.lbmk@test.local", "Prof Lesson Bookmark");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		Long lessonId = course.lessonIds().get(0);

		UserEntity student = createUser("student.lbmk@test.local", "Student Lesson Bookmark");
		String studentToken = tokenFor(student);

		// Enroll student
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		// Bookmark lesson
		String resp = mockMvc.perform(post("/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks",
						course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetType").value("LESSON"))
				.andExpect(jsonPath("$.lessonId").value(lessonId))
				.andReturn().getResponse().getContentAsString();

		JsonNode json = objectMapper.readTree(resp);
		long bookmarkId = json.get("id").asLong();

		// Status is bookmarked
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks/status",
						course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookmarked").value(true));

		// Delete by bookmark ID
		mockMvc.perform(delete("/api/v1/bookmarks/{bookmarkId}", bookmarkId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNoContent());

		// Status is now false
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks/status",
						course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookmarked").value(false));
	}

	@Test
	@DisplayName("Learner bookmarks are strictly isolated between users")
	void learnerBookmarksAreIsolated() throws Exception {
		UserEntity instructor = createInstructor("prof.iso@test.local", "Prof Iso");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student1 = createUser("s1.bmk@test.local", "Student 1");
		String token1 = tokenFor(student1);

		UserEntity student2 = createUser("s2.bmk@test.local", "Student 2");
		String token2 = tokenFor(student2);

		// Student 1 bookmarks course
		String resp = mockMvc.perform(post("/api/v1/courses/{courseId}/bookmarks", course.courseId())
						.header("Authorization", bearer(token1)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long bmkId1 = objectMapper.readTree(resp).get("id").asLong();

		// Student 2 sees empty bookmarks list
		mockMvc.perform(get("/api/v1/bookmarks")
						.header("Authorization", bearer(token2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		// Student 2 cannot delete Student 1's bookmark
		mockMvc.perform(delete("/api/v1/bookmarks/{bookmarkId}", bmkId1)
						.header("Authorization", bearer(token2)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Unauthenticated requests are rejected")
	void unauthenticatedRequestsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/bookmarks"))
				.andExpect(status().isUnauthorized());
	}
}
