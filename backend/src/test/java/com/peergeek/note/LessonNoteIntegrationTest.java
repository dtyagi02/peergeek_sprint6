package com.peergeek.note;

import com.peergeek.enrollment.LearningTestSupport;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LessonNoteIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Enrolled student can create, retrieve, update, and delete private lesson notes")
	void noteLifecycle() throws Exception {
		UserEntity instructor = createInstructor("prof.note@test.local", "Prof Note");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		Long lessonId = course.lessonIds().get(0);

		UserEntity student = createUser("student.note@test.local", "Student Note");
		String studentToken = tokenFor(student);

		// Enroll student
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		// Initially, no note exists -> 204 No Content
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNoContent());

		// Save note
		mockMvc.perform(put("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"Important notes on distributed transactions.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Important notes on distributed transactions."))
				.andExpect(jsonPath("$.lessonId").value(lessonId))
				.andExpect(jsonPath("$.userId").value(student.getId()));

		// Verify exactly 1 row in DB
		Integer count = jdbc.queryForObject(
				"select count(*) from lesson_notes where user_id = ? and lesson_id = ?",
				Integer.class, student.getId(), lessonId);
		assertThat(count).isEqualTo(1);

		// Get note
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Important notes on distributed transactions."));

		// Update note
		mockMvc.perform(put("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"Updated: Remember 2PC caveats and timeouts.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Updated: Remember 2PC caveats and timeouts."));

		// Still 1 row in DB (enforcing 1 note per user per lesson)
		Integer countAfterUpdate = jdbc.queryForObject(
				"select count(*) from lesson_notes where user_id = ? and lesson_id = ?",
				Integer.class, student.getId(), lessonId);
		assertThat(countAfterUpdate).isEqualTo(1);

		// Delete note
		mockMvc.perform(delete("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNoContent());

		// Get note returns 204
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("Unenrolled user cannot access or save notes")
	void unenrolledCannotAccessNotes() throws Exception {
		UserEntity instructor = createInstructor("prof.unenr@test.local", "Prof Unenr");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		Long lessonId = course.lessonIds().get(0);

		UserEntity student = createUser("student.unenr@test.local", "Student Unenr");
		String studentToken = tokenFor(student);

		// Try to GET note without enrolling -> 403 Forbidden
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));

		// Try to PUT note without enrolling -> 403 Forbidden
		mockMvc.perform(put("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"Hacking attempt\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Learner notes are strictly private between users")
	void notesAreStrictlyPrivateBetweenUsers() throws Exception {
		UserEntity instructor = createInstructor("prof.priv@test.local", "Prof Priv");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		Long lessonId = course.lessonIds().get(0);

		UserEntity student1 = createUser("s1.note@test.local", "Student 1");
		String token1 = tokenFor(student1);
		UserEntity student2 = createUser("s2.note@test.local", "Student 2");
		String token2 = tokenFor(student2);

		// Enroll both students
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token1))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token2))).andExpect(status().isOk());

		// Student 1 saves note
		mockMvc.perform(put("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(token1))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"Confidential learner notes 1\"}"))
				.andExpect(status().isOk());

		// Student 2 requests note -> returns 204 No Content (cannot see Student 1's note!)
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(token2)))
				.andExpect(status().isNoContent());

		// Student 2 saves their own note
		mockMvc.perform(put("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(token2))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"Student 2 personal notes\"}"))
				.andExpect(status().isOk());

		// Student 1 still sees only their note
		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(token1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Confidential learner notes 1"));

		// Student 2 deletes note -> does not affect Student 1
		mockMvc.perform(delete("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(token2)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/{lessonId}/notes", course.courseId(), lessonId)
						.header("Authorization", bearer(token1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Confidential learner notes 1"));
	}

	@Test
	@DisplayName("Invalid lesson ID returns 404")
	void invalidLessonReturnsNotFound() throws Exception {
		UserEntity instructor = createInstructor("prof.inv@test.local", "Prof Inv");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student = createUser("student.inv@test.local", "Student Inv");
		String studentToken = tokenFor(student);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(studentToken))).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/courses/{courseId}/lessons/999999/notes", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNotFound());
	}
}
