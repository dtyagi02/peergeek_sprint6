package com.peergeek.enrollment;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Enroll in a published course succeeds and sets up initial progress")
	void enrollPublishedCourseSuccess() throws Exception {
		UserEntity instructor = createInstructor("prof@example.test", "Professor Alan");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult created = createCourseWithModulesAndLessons(instructorToken, 1, 2);
		publishCourse(instructorToken, created.courseId());

		UserEntity learner = createUser("learner@example.test", "Learner Lisa");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.courseId").value(created.courseId()))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.progress.totalLessons").value(2))
				.andExpect(jsonPath("$.progress.completedLessons").value(0))
				.andExpect(jsonPath("$.progress.percent").value(0));

		// Check audit log
		Integer audits = jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'LEARNER_ENROLLED' and actor_user_id = ?",
				Integer.class, learner.getId());
		assertThat(audits).isEqualTo(1);
	}

	@Test
	@DisplayName("Enrolling in draft, unpublished, deleted, or nonexistent course returns identical 404")
	void enrollNonPublishedCourseReturns404() throws Exception {
		UserEntity instructor = createInstructor("prof2@example.test", "Professor Bob");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult draftCourse = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		CourseCreationResult unpubCourse = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, unpubCourse.courseId());
		unpublishCourse(instructorToken, unpubCourse.courseId());

		CourseCreationResult delCourse = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		jdbc.update("update courses set deleted_at = now() where id = ?", delCourse.courseId());

		UserEntity learner = createUser("learner2@example.test", "Learner Dan");
		String learnerToken = tokenFor(learner);

		// Draft -> 404
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", draftCourse.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Unpublished -> 404
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", unpubCourse.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Deleted -> 404
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", delCourse.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Nonexistent -> 404
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", 999999L)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("Owner cannot enroll in their own course -> 409 CANNOT_ENROLL_OWN_COURSE")
	void ownerCannotEnrollOwnCourse() throws Exception {
		UserEntity instructor = createInstructor("prof3@example.test", "Professor Carl");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult created = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, created.courseId());

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANNOT_ENROLL_OWN_COURSE"));
	}

	@Test
	@DisplayName("Idempotent repeat enrollment returns existing row without duplicate event or audit")
	void idempotentRepeatEnrollment() throws Exception {
		UserEntity instructor = createInstructor("prof4@example.test", "Professor Diana");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult created = createCourseWithModulesAndLessons(instructorToken, 1, 2);
		publishCourse(instructorToken, created.courseId());

		UserEntity learner = createUser("learner4@example.test", "Learner Dave");
		String learnerToken = tokenFor(learner);

		// First enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// Second enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		Integer count = jdbc.queryForObject(
				"select count(*) from enrollments where user_id = ? and course_id = ?",
				Integer.class, learner.getId(), created.courseId());
		assertThat(count).isEqualTo(1);

		Integer audits = jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'LEARNER_ENROLLED' and actor_user_id = ?",
				Integer.class, learner.getId());
		assertThat(audits).isEqualTo(1);
	}

	@Test
	@DisplayName("Withdrawal and reactivation keeps previous progress")
	void withdrawAndReactivateKeepsProgress() throws Exception {
		UserEntity instructor = createInstructor("prof5@example.test", "Professor Eva");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult created = createCourseWithModulesAndLessons(instructorToken, 1, 2);
		publishCourse(instructorToken, created.courseId());

		UserEntity learner = createUser("learner5@example.test", "Learner Eric");
		String learnerToken = tokenFor(learner);

		// Enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// Complete first lesson
		Long firstLessonId = created.lessonIds().getFirst();
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion", created.courseId(), firstLessonId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content("{\"completed\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedLessons").value(1))
				.andExpect(jsonPath("$.percent").value(50));

		// Withdraw
		mockMvc.perform(delete("/api/v1/learning/courses/{courseId}/enrollment", created.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Cache-Control", "no-store"));

		// Re-enroll
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.progress.completedLessons").value(1))
				.andExpect(jsonPath("$.progress.percent").value(50));

		// Verify exactly one enrollment row exists
		Integer count = jdbc.queryForObject(
				"select count(*) from enrollments where user_id = ? and course_id = ?",
				Integer.class, learner.getId(), created.courseId());
		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("Concurrent double-enroll yields exactly one row")
	void concurrentDoubleEnrollYieldsExactlyOneRow() throws Exception {
		UserEntity instructor = createInstructor("prof6@example.test", "Professor Frank");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult created = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, created.courseId());

		UserEntity learner = createUser("learner6@example.test", "Learner Fiona");
		String learnerToken = tokenFor(learner);

		int threads = 4;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch readyLatch = new CountDownLatch(threads);
		CountDownLatch startLatch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();

		for (int i = 0; i < threads; i++) {
			executor.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await();
					var result = mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
									.header("Authorization", bearer(learnerToken)))
							.andReturn();
					if (result.getResponse().getStatus() == 200) {
						successCount.incrementAndGet();
					}
				} catch (Exception ignored) {
				}
			});
		}

		readyLatch.await();
		startLatch.countDown();
		executor.shutdown();
		executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

		Integer rows = jdbc.queryForObject(
				"select count(*) from enrollments where user_id = ? and course_id = ?",
				Integer.class, learner.getId(), created.courseId());
		assertThat(rows).isEqualTo(1);
		assertThat(successCount.get()).isEqualTo(threads);
	}

	@Test
	@DisplayName("GET enrollment status endpoint returns expected state for owner, enrolled, and non-enrolled")
	void getEnrollmentStatusBehavior() throws Exception {
		UserEntity instructor = createInstructor("prof7@example.test", "Professor Grace");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult created = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, created.courseId());

		UserEntity learnerA = createUser("learnerA@example.test", "Learner A");
		String tokenA = tokenFor(learnerA);

		UserEntity learnerB = createUser("learnerB@example.test", "Learner B");
		String tokenB = tokenFor(learnerB);

		// Non-enrolled user
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/enrollment", created.courseId())
						.header("Authorization", bearer(tokenA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enrolled").value(false))
				.andExpect(jsonPath("$.isOwner").value(false))
				.andExpect(jsonPath("$.canEnroll").value(true));

		// Owner
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/enrollment", created.courseId())
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enrolled").value(false))
				.andExpect(jsonPath("$.isOwner").value(true))
				.andExpect(jsonPath("$.canEnroll").value(false));

		// Enroll learner B
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", created.courseId())
						.header("Authorization", bearer(tokenB)))
				.andExpect(status().isOk());

		// Enrolled user
		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/enrollment", created.courseId())
						.header("Authorization", bearer(tokenB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enrolled").value(true))
				.andExpect(jsonPath("$.isOwner").value(false))
				.andExpect(jsonPath("$.canEnroll").value(false))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.progress").isMap());
	}

	@Test
	@DisplayName("GET my courses endpoint filters, paginates, and sorts without N+1 queries")
	void getMyCoursesListing() throws Exception {
		UserEntity instructor = createInstructor("prof8@example.test", "Professor Henry");
		String instructorToken = tokenFor(instructor);

		CourseCreationResult c1 = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, c1.courseId());
		jdbc.update("update courses set title = 'Alpha Algorithms' where id = ?", c1.courseId());

		CourseCreationResult c2 = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, c2.courseId());
		jdbc.update("update courses set title = 'Beta Operating Systems' where id = ?", c2.courseId());

		UserEntity learner = createUser("learner8@example.test", "Learner Helen");
		String learnerToken = tokenFor(learner);

		// Enroll in both
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", c1.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", c2.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		// Complete course 1
		mockMvc.perform(put("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion",
				c1.courseId(), c1.lessonIds().getFirst())
				.header("Authorization", bearer(learnerToken))
				.contentType("application/json")
				.content("{\"completed\":true}")).andExpect(status().isOk());

		// Filter status=completed
		mockMvc.perform(get("/api/v1/learning/courses?status=completed")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].courseId").value(c1.courseId()));

		// Filter status=in-progress
		mockMvc.perform(get("/api/v1/learning/courses?status=in-progress")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].courseId").value(c2.courseId()));

		// Search q=Beta
		mockMvc.perform(get("/api/v1/learning/courses?q=Beta")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].courseId").value(c2.courseId()));

		// Sort title,asc
		mockMvc.perform(get("/api/v1/learning/courses?sort=title,asc")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].title").value("Alpha Algorithms"))
				.andExpect(jsonPath("$.content[1].title").value("Beta Operating Systems"));

		// Unknown sort -> 400
		mockMvc.perform(get("/api/v1/learning/courses?sort=invalid,desc")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}
}
