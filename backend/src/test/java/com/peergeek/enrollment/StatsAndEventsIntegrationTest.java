package com.peergeek.enrollment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(StatsAndEventsIntegrationTest.TestListenerConfig.class)
class StatsAndEventsIntegrationTest extends LearningTestSupport {

	@Autowired
	private LearningEventListener testListener;

	@TestConfiguration
	static class TestListenerConfig {
		@Bean
		public LearningEventListener learningEventListener() {
			return new LearningEventListener();
		}
	}

	static class LearningEventListener {
		private final CopyOnWriteArrayList<Object> received = new CopyOnWriteArrayList<>();

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		void onEnrolled(LearnerEnrolledEvent event) {
			received.add(event);
		}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		void onWithdrawn(LearnerWithdrawnEvent event) {
			received.add(event);
		}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		void onLessonCompleted(LessonCompletedEvent event) {
			received.add(event);
		}

		List<Object> events() {
			return List.copyOf(received);
		}

		void clear() {
			received.clear();
		}
	}

	@AfterEach
	void clearEvents() {
		testListener.clear();
	}

	@Test
	@DisplayName("Events publish exactly once after commit and never on idempotent repeats")
	void eventsPublishOnceAfterCommit() throws Exception {
		UserEntity instructor = createInstructor("prof_stats1@example.test", "Instructor Stats 1");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner = createUser("learner_stats1@example.test", "Learner Stats 1");
		String learnerToken = tokenFor(learner);

		testListener.clear();

		// 1. Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		assertThat(testListener.events()).hasSize(1);
		assertThat(testListener.events().get(0)).isInstanceOf(LearnerEnrolledEvent.class);
		LearnerEnrolledEvent enrolledEvent = (LearnerEnrolledEvent) testListener.events().get(0);
		assertThat(enrolledEvent.courseId()).isEqualTo(course.courseId());
		assertThat(enrolledEvent.userId()).isEqualTo(learner.getId());

		// Idempotent enroll -> NO new event
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());
		assertThat(testListener.events()).hasSize(1);

		// 2. Complete lesson
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion",
				course.courseId(), course.lessonIds().getFirst())
				.header("Authorization", bearer(learnerToken))
				.contentType("application/json")
				.content("{\"completed\":true}")).andExpect(status().isOk());

		assertThat(testListener.events()).hasSize(2);
		assertThat(testListener.events().get(1)).isInstanceOf(LessonCompletedEvent.class);
		LessonCompletedEvent compEvent = (LessonCompletedEvent) testListener.events().get(1);
		assertThat(compEvent.completedLessons()).isEqualTo(1);
		assertThat(compEvent.totalLessons()).isEqualTo(1);

		// Idempotent complete -> NO new event
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion",
				course.courseId(), course.lessonIds().getFirst())
				.header("Authorization", bearer(learnerToken))
				.contentType("application/json")
				.content("{\"completed\":true}")).andExpect(status().isOk());
		assertThat(testListener.events()).hasSize(2);

		// 3. Withdraw
		mockMvc.perform(delete("/api/v1/learning/courses/{courseId}/enrollment", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isNoContent());

		assertThat(testListener.events()).hasSize(3);
		assertThat(testListener.events().get(2)).isInstanceOf(LearnerWithdrawnEvent.class);

		// Idempotent withdraw -> NO new event
		mockMvc.perform(delete("/api/v1/learning/courses/{courseId}/enrollment", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isNoContent());
		assertThat(testListener.events()).hasSize(3);
	}

	@Test
	@DisplayName("Stats refresher records absolute numbers into CourseStatsRecorder and public discovery reflects them")
	void statsRefresherUpdatesPublicDiscovery() throws Exception {
		UserEntity instructor = createInstructor("prof_stats2@example.test", "Instructor Stats 2");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner1 = createUser("learner_stats2a@example.test", "Learner Stats 2A");
		String token1 = tokenFor(learner1);

		UserEntity learner2 = createUser("learner_stats2b@example.test", "Learner Stats 2B");
		String token2 = tokenFor(learner2);

		// Initially public preview is available
		mockMvc.perform(get("/api/v1/public/courses/{courseId}", course.courseId()))
				.andExpect(status().isOk());

		// Learner 1 enrolls
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token1))).andExpect(status().isOk());

		// Public preview reflects 1 enrollment
		mockMvc.perform(get("/api/v1/public/courses/{courseId}", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stats.enrollmentCount").value(1))
				.andExpect(jsonPath("$.stats.completionCount").value(0));

		// Learner 2 enrolls and completes
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(token2))).andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion",
				course.courseId(), course.lessonIds().getFirst())
				.header("Authorization", bearer(token2))
				.contentType("application/json")
				.content("{\"completed\":true}")).andExpect(status().isOk());

		// Public preview reflects 2 enrollments, 1 completion
		mockMvc.perform(get("/api/v1/public/courses/{courseId}", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stats.enrollmentCount").value(2))
				.andExpect(jsonPath("$.stats.completionCount").value(1));

		// Learner 1 withdraws
		mockMvc.perform(delete("/api/v1/learning/courses/{courseId}/enrollment", course.courseId())
				.header("Authorization", bearer(token1))).andExpect(status().isNoContent());

		// Public preview reflects 1 active enrollment, 1 completion
		mockMvc.perform(get("/api/v1/public/courses/{courseId}", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stats.enrollmentCount").value(1))
				.andExpect(jsonPath("$.stats.completionCount").value(1));
	}
}
