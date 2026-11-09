package com.peergeek.enrollment;

import java.util.List;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContinueLearningIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Continue learning: FIRST, RESUME, NEXT, COMPLETED cases across modules")
	void continueLearningTransitions() throws Exception {
		UserEntity instructor = createInstructor("prof_cont1@example.test", "Instructor Cont 1");
		String instructorToken = tokenFor(instructor);
		// 2 modules x 2 lessons = 4 lessons: [l1, l2, l3, l4]
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 2, 2);
		publishCourse(instructorToken, course.courseId());

		Long l1 = course.lessonIds().get(0);
		Long l2 = course.lessonIds().get(1);
		Long l3 = course.lessonIds().get(2);
		Long l4 = course.lessonIds().get(3);

		UserEntity learner = createUser("learner_cont1@example.test", "Learner Cont 1");
		String learnerToken = tokenFor(learner);

		// Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// 1. Initial state (no visit): rule (3) FIRST lesson
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/continue", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").value(l1))
				.andExpect(jsonPath("$.reason").value("FIRST"));

		// 2. Visit L2 (without completing it): rule (1) RESUME
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/visit", course.courseId(), l2)
				.header("Authorization", bearer(learnerToken))).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/continue", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").value(l2))
				.andExpect(jsonPath("$.reason").value("RESUME"));

		// 3. Complete L2: rule (2) NEXT incomplete lesson after L2 in course order (crosses module boundary to L3!)
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l2)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/continue", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").value(l3))
				.andExpect(jsonPath("$.reason").value("NEXT"));

		// 4. Visit and complete L4, leaving L1 incomplete: since no incomplete lesson after L4, rule (3) FIRST incomplete -> L1
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/visit", course.courseId(), l4)
				.header("Authorization", bearer(learnerToken))).andExpect(status().isNoContent());

		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l4)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/continue", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").value(l1))
				.andExpect(jsonPath("$.reason").value("FIRST"));

		// 5. Complete remaining lessons (L1 and L3): rule (4) COMPLETED
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l1)
				.header("Authorization", bearer(learnerToken))
				.contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", course.courseId(), l3)
				.header("Authorization", bearer(learnerToken))
				.contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/continue", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").doesNotExist())
				.andExpect(jsonPath("$.reason").value("COMPLETED"));
	}

	@Test
	@DisplayName("Continue learning falls back when last visited lesson is deleted")
	void continueLearningWhenLastLessonDeleted() throws Exception {
		UserEntity instructor = createInstructor("prof_cont2@example.test", "Instructor Cont 2");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 2);
		publishCourse(instructorToken, course.courseId());

		Long l1 = course.lessonIds().get(0);
		Long l2 = course.lessonIds().get(1);

		UserEntity learner = createUser("learner_cont2@example.test", "Learner Cont 2");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Visit L2
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/visit", course.courseId(), l2)
				.header("Authorization", bearer(learnerToken))).andExpect(status().isNoContent());

		// Instructor deletes L2
		mockMvc.perform(delete("/api/v1/instructor/lessons/{lessonId}", l2)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isNoContent());

		// Continue should fall back to first incomplete lesson (L1)
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/continue", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").value(l1))
				.andExpect(jsonPath("$.reason").value("FIRST"));
	}
}
