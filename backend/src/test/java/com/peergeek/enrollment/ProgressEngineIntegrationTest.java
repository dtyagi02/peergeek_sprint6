package com.peergeek.enrollment;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProgressEngineIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Progress math: floor percentage, 0 lessons, and mark/unmark idempotence")
	void progressMathAndIdempotence() throws Exception {
		UserEntity instructor = createInstructor("prof_prog1@example.test", "Instructor Prog 1");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 3);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner = createUser("learner_prog1@example.test", "Learner Prog 1");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		Long l1 = course.lessonIds().get(0);
		Long l2 = course.lessonIds().get(1);
		Long l3 = course.lessonIds().get(2);

		// Complete 1 lesson: 1 / 3 = 33% floor
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l1)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedLessons").value(1))
				.andExpect(jsonPath("$.totalLessons").value(3))
				.andExpect(jsonPath("$.percent").value(33));

		// Idempotent mark complete again: remains 1 lesson, 33%
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l1)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedLessons").value(1))
				.andExpect(jsonPath("$.percent").value(33));

		// Complete 2nd lesson: 2 / 3 = 66% floor
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l2)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedLessons").value(2))
				.andExpect(jsonPath("$.percent").value(66));

		// Uncomplete 2nd lesson: back to 1 lesson, 33%
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l2)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedLessons").value(1))
				.andExpect(jsonPath("$.percent").value(33));

		// Idempotent uncomplete again: remains 1 lesson, 33%
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l2)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedLessons").value(1))
				.andExpect(jsonPath("$.percent").value(33));
	}

	@Test
	@DisplayName("Course with 0 lessons has 0% progress and completed_at is null")
	void zeroLessonsHasZeroPercent() throws Exception {
		UserEntity instructor = createInstructor("prof_prog0@example.test", "Instructor Prog 0");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		// Delete the only lesson directly
		jdbc.update("delete from lesson_contents where lesson_id = ?", course.lessonIds().getFirst());
		jdbc.update("delete from lessons where id = ?", course.lessonIds().getFirst());

		UserEntity learner = createUser("learner_prog0@example.test", "Learner Prog 0");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.progress.totalLessons").value(0))
				.andExpect(jsonPath("$.progress.completedLessons").value(0))
				.andExpect(jsonPath("$.progress.percent").value(0))
				.andExpect(jsonPath("$.progress.completedAt").doesNotExist());
	}

	@Test
	@DisplayName("Lesson added after 100% completion drops percent but preserves completed_at")
	void lessonAddedAfterCompletionKeepsCompletedAt() throws Exception {
		UserEntity instructor = createInstructor("prof_prog2@example.test", "Instructor Prog 2");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		Long initialLessonId = course.lessonIds().getFirst();

		UserEntity learner = createUser("learner_prog2@example.test", "Learner Prog 2");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Complete the single lesson -> 100%
		MvcResult comp1 = mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), initialLessonId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.percent").value(100))
				.andExpect(jsonPath("$.completedAt").isString())
				.andReturn();

		String originalCompletedAt = objectMapper.readTree(comp1.getResponse().getContentAsString()).get("completedAt").asText();
		assertThat(originalCompletedAt).isNotBlank();

		// Instructor adds a 2nd lesson
		Long moduleId = jdbc.queryForObject("select module_id from lessons where id = ?", Long.class, initialLessonId);
		MvcResult newLessonRes = mockMvc.perform(post("/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content("{\"title\":\"Bonus Lesson\",\"summary\":\"Added later\"}"))
				.andExpect(status().isCreated()).andReturn();
		long newLessonId = objectMapper.readTree(newLessonRes.getResponse().getContentAsString()).get("id").asLong();

		// Add content for new lesson
		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", newLessonId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content("{\"document\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Bonus\"}]}]},\"baseVersion\":0}"))
				.andExpect(status().isOk());

		// Reading outline or enrollment status recomputes stale progress lazily
		MvcResult outlineRes = mockMvc.perform(get("/api/v1/learning/courses/{courseId}", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode progressNode = objectMapper.readTree(outlineRes.getResponse().getContentAsString()).get("progress");
		assertThat(progressNode.get("totalLessons").asInt()).isEqualTo(2);
		assertThat(progressNode.get("completedLessons").asInt()).isEqualTo(1);
		assertThat(progressNode.get("percent").asInt()).isEqualTo(50);
		// completedAt is PRESERVED!
		assertThat(java.time.Instant.parse(progressNode.get("completedAt").asText()).truncatedTo(java.time.temporal.ChronoUnit.MICROS))
				.isEqualTo(java.time.Instant.parse(originalCompletedAt).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
	}

	@Test
	@DisplayName("Deleted lesson's completed row is ignored in progress calculations")
	void deletedLessonIgnoredInProgress() throws Exception {
		UserEntity instructor = createInstructor("prof_prog3@example.test", "Instructor Prog 3");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 2);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner = createUser("learner_prog3@example.test", "Learner Prog 3");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		Long l1 = course.lessonIds().get(0);
		Long l2 = course.lessonIds().get(1);

		// Complete both lessons
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l1)
				.header("Authorization", bearer(learnerToken)).contentType("application/json").content("{\"completed\":true}"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l2)
				.header("Authorization", bearer(learnerToken)).contentType("application/json").content("{\"completed\":true}"))
				.andExpect(status().isOk());

		// Instructor deletes lesson 2
		mockMvc.perform(delete("/api/v1/instructor/lessons/{lessonId}", l2)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isNoContent());

		// Now course has only 1 lesson (l1) which is completed -> 100%
		MvcResult outline = mockMvc.perform(get("/api/v1/learning/courses/{courseId}", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode progress = objectMapper.readTree(outline.getResponse().getContentAsString()).get("progress");
		assertThat(progress.get("totalLessons").asInt()).isEqualTo(1);
		assertThat(progress.get("completedLessons").asInt()).isEqualTo(1);
		assertThat(progress.get("percent").asInt()).isEqualTo(100);
	}

	@Test
	@DisplayName("Completing a lesson from another course returns 404")
	void completingLessonFromAnotherCourseReturns404() throws Exception {
		UserEntity instructor = createInstructor("prof_prog4@example.test", "Instructor Prog 4");
		String instructorToken = tokenFor(instructor);

		CourseCreationResult courseA = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseA.courseId());

		CourseCreationResult courseB = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseB.courseId());

		UserEntity learner = createUser("learner_prog4@example.test", "Learner Prog 4");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", courseA.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Attempt to complete lesson from course B under course A
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion",
						courseA.courseId(), courseB.lessonIds().getFirst())
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}
}
