package com.peergeek.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.assignment.AssignmentTestSupport;
import com.peergeek.common.SafeCache;
import com.peergeek.enrollment.LearnerEnrolledEvent;
import com.peergeek.user.UserEntity;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class InstructorAnalyticsIntegrationTest extends AssignmentTestSupport {

	@Autowired
	private AnalyticsCacheService analyticsCacheService;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("Instructor can retrieve analytics for owned course calculating all 8 metrics accurately")
	void instructorCanRetrieveAnalyticsForOwnedCourse() throws Exception {
		UserEntity instructor = createInstructor("prof.metrics@test.local", "Prof Metrics");
		String instructorToken = tokenFor(instructor);

		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 2);
		publishCourse(instructorToken, courseId);

		// Enroll 2 students
		UserEntity student1 = createUser("s1@test.local", "Student One");
		String s1Token = tokenFor(student1);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", courseId)
						.header("Authorization", bearer(s1Token)))
				.andExpect(status().isOk());

		UserEntity student2 = createUser("s2@test.local", "Student Two");
		String s2Token = tokenFor(student2);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", courseId)
						.header("Authorization", bearer(s2Token)))
				.andExpect(status().isOk());

		Long s1EnrollmentId = jdbc.queryForObject(
				"SELECT id FROM enrollments WHERE course_id = ? AND user_id = ?",
				Long.class, courseId, student1.getId());
		Long s2EnrollmentId = jdbc.queryForObject(
				"SELECT id FROM enrollments WHERE course_id = ? AND user_id = ?",
				Long.class, courseId, student2.getId());

		Long lesson1Id = jdbc.queryForObject(
				"SELECT l.id FROM lessons l JOIN course_modules m ON l.module_id = m.id WHERE m.course_id = ? ORDER BY l.id ASC LIMIT 1",
				Long.class, courseId);
		Long lesson2Id = jdbc.queryForObject(
				"SELECT l.id FROM lessons l JOIN course_modules m ON l.module_id = m.id WHERE m.course_id = ? ORDER BY l.id DESC LIMIT 1",
				Long.class, courseId);

		// S1 completes lesson 1 & 2
		jdbc.update("INSERT INTO lesson_progress (enrollment_id, lesson_id, completed_at, created_at, updated_at) VALUES (?, ?, NOW(), NOW(), NOW())",
				s1EnrollmentId, lesson1Id);
		jdbc.update("INSERT INTO lesson_progress (enrollment_id, lesson_id, completed_at, created_at, updated_at) VALUES (?, ?, NOW(), NOW(), NOW())",
				s1EnrollmentId, lesson2Id);
		// Mark S1 completed in course_progress
		jdbc.update("UPDATE course_progress SET completed_at = NOW(), completed_lessons = 2, percent = 100 WHERE enrollment_id = ?",
				s1EnrollmentId);

		// S2 completes only lesson 1
		jdbc.update("INSERT INTO lesson_progress (enrollment_id, lesson_id, completed_at, created_at, updated_at) VALUES (?, ?, NOW(), NOW(), NOW())",
				s2EnrollmentId, lesson1Id);

		// Create published assignment 1 (max_points = 100)
		jdbc.update("INSERT INTO assignments (course_id, kind, title, instructions, status, max_points, created_by, created_at, updated_at) VALUES (?, 'PROJECT', 'Assignment 1', 'Instructions 1', 'PUBLISHED', 100.0, ?, NOW(), NOW())",
				courseId, instructor.getId());
		Long a1Id = jdbc.queryForObject("SELECT id FROM assignments WHERE course_id = ? AND title = 'Assignment 1'", Long.class, courseId);

		// Create published assignment 2 (max_points = 50)
		jdbc.update("INSERT INTO assignments (course_id, kind, title, instructions, status, max_points, created_by, created_at, updated_at) VALUES (?, 'PROJECT', 'Assignment 2', 'Instructions 2', 'PUBLISHED', 50.0, ?, NOW(), NOW())",
				courseId, instructor.getId());
		Long a2Id = jdbc.queryForObject("SELECT id FROM assignments WHERE course_id = ? AND title = 'Assignment 2'", Long.class, courseId);

		// S1 submits A1 -> GRADED with 90 points (90%)
		jdbc.update("INSERT INTO submissions (assignment_id, user_id, status, total_points, started_at, submitted_at, graded_at, created_at, updated_at) VALUES (?, ?, 'GRADED', 90.0, NOW(), NOW(), NOW(), NOW(), NOW())",
				a1Id, student1.getId());

		// S2 submits A1 -> GRADED with 70 points (70%)
		jdbc.update("INSERT INTO submissions (assignment_id, user_id, status, total_points, started_at, submitted_at, graded_at, created_at, updated_at) VALUES (?, ?, 'GRADED', 70.0, NOW(), NOW(), NOW(), NOW(), NOW())",
				a1Id, student2.getId());

		// S1 submits A2 -> SUBMITTED (pending review)
		jdbc.update("INSERT INTO submissions (assignment_id, user_id, status, started_at, submitted_at, created_at, updated_at) VALUES (?, ?, 'SUBMITTED', NOW(), NOW(), NOW(), NOW())",
				a2Id, student1.getId());

		// S1 reviews course: 5 stars
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", courseId)
						.header("Authorization", bearer(s1Token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 5, \"reviewText\": \"Superb course!\"}"))
				.andExpect(status().isOk());

		// S2 reviews course: 4 stars
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", courseId)
						.header("Authorization", bearer(s2Token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 4, \"reviewText\": \"Very helpful!\"}"))
				.andExpect(status().isOk());

		// Query analytics endpoint
		MvcResult result = mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(courseId))
				.andExpect(jsonPath("$.totalEnrollments").value(2))
				.andExpect(jsonPath("$.activeLearners").value(2))
				.andExpect(jsonPath("$.completionRate").value(50.0))
				.andExpect(jsonPath("$.averageAssessmentScore").value(80.0))
				.andExpect(jsonPath("$.averageRating").value(4.5))
				.andExpect(jsonPath("$.ratingCount").value(2))
				.andExpect(jsonPath("$.assignmentSubmissionRate").value(75.0))
				.andExpect(jsonPath("$.pendingSubmissions").value(1))
				.andExpect(jsonPath("$.lessonDropOff").isArray())
				.andExpect(jsonPath("$.lessonDropOff.length()").value(2))
				.andExpect(jsonPath("$.assignmentStats").isArray())
				.andExpect(jsonPath("$.assignmentStats.length()").value(2))
				.andExpect(jsonPath("$.scoreDistribution").isArray())
				.andReturn();

		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(json.get("lessonDropOff").get(0).get("completedCount").asLong()).isEqualTo(2);
		assertThat(json.get("lessonDropOff").get(1).get("completedCount").asLong()).isEqualTo(1);
		assertThat(json.get("lessonDropOff").get(1).get("dropOffRate").asDouble()).isEqualTo(50.0);
	}

	@Test
	@DisplayName("Empty course returns safe zero and null values without division by zero errors")
	void emptyCourseReturnsSafeDefaults() throws Exception {
		UserEntity instructor = createInstructor("prof.empty@test.local", "Prof Empty");
		String instructorToken = tokenFor(instructor);

		long courseId = createCourseWithModulesAndLessons(instructorToken, 0, 0);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(courseId))
				.andExpect(jsonPath("$.totalEnrollments").value(0))
				.andExpect(jsonPath("$.activeLearners").value(0))
				.andExpect(jsonPath("$.completionRate").value(0.0))
				.andExpect(jsonPath("$.averageAssessmentScore").doesNotExist())
				.andExpect(jsonPath("$.averageRating").doesNotExist())
				.andExpect(jsonPath("$.ratingCount").value(0))
				.andExpect(jsonPath("$.assignmentSubmissionRate").value(0.0))
				.andExpect(jsonPath("$.pendingSubmissions").value(0))
				.andExpect(jsonPath("$.lessonDropOff").isEmpty())
				.andExpect(jsonPath("$.assignmentStats").isEmpty());
	}

	@Test
	@DisplayName("Instructor cannot view another instructor's course analytics")
	void instructorCannotAccessAnotherInstructorsCourseAnalytics() throws Exception {
		UserEntity instructorA = createInstructor("prof.a@test.local", "Prof A");
		UserEntity instructorB = createInstructor("prof.b@test.local", "Prof B");

		long courseId = createCourseWithModulesAndLessons(tokenFor(instructorA), 1, 1);

		// Instructor B tries to access Instructor A's analytics
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId)
						.header("Authorization", bearer(tokenFor(instructorB))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Anonymous unauthenticated request is rejected with 401")
	void anonymousRequestIsRejected() throws Exception {
		UserEntity instructor = createInstructor("prof.anon@test.local", "Prof Anon");
		long courseId = createCourseWithModulesAndLessons(tokenFor(instructor), 1, 1);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Redis cache stores analytics and invalidates when domain event is published")
	void cacheStoresAndInvalidatesOnDomainEvent() throws Exception {
		UserEntity instructor = createInstructor("prof.cache@test.local", "Prof Cache");
		String token = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(token, 1, 1);

		// First call populates cache
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEnrollments").value(0));

		String cacheKey = "analytics:course:" + courseId;
		assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

		// Simulate direct DB change (e.g. background insertion of enrollment)
		UserEntity student = createUser("cache.learner@test.local", "Cache Learner");
		jdbc.update("INSERT INTO enrollments (course_id, user_id, status, enrolled_at, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW(), NOW())",
				courseId, student.getId());

		// Without invalidation, cache returns 0
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEnrollments").value(0));

		// Publish domain event
		eventPublisher.publishEvent(new LearnerEnrolledEvent(courseId, student.getId(), 999L));

		// Cache key should now be evicted
		assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

		// Subsequent call recomputes fresh data from DB (totalEnrollments = 1)
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/analytics", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEnrollments").value(1));
	}

	@Test
	@DisplayName("SafeCache degrades gracefully when Redis connection fails")
	void safeCacheGracefulDegradationOnRedisFailure() {
		StringRedisTemplate mockRedis = org.mockito.Mockito.mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
		org.mockito.Mockito.when(mockRedis.opsForValue()).thenReturn(ops);
		org.mockito.Mockito.when(ops.get(org.mockito.ArgumentMatchers.anyString()))
				.thenThrow(new RedisConnectionFailureException("Redis connection refused"));

		SafeCache cache = new SafeCache(mockRedis, objectMapper);

		// Should not throw, should return fallback source of truth directly
		String result = cache.getOrLoad("test-key", String.class, Duration.ofSeconds(60), () -> "mysql-source-of-truth");
		assertThat(result).isEqualTo("mysql-source-of-truth");
	}
}
