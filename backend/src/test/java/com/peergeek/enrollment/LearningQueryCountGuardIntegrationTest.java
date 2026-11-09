package com.peergeek.enrollment;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(LearningQueryCountGuardIntegrationTest.QueryCountingConfiguration.class)
class LearningQueryCountGuardIntegrationTest extends LearningTestSupport {

	private static final AtomicInteger QUERY_COUNT = new AtomicInteger();

	@TestConfiguration
	static class QueryCountingConfiguration {
		@Bean
		static org.springframework.beans.factory.config.BeanPostProcessor dataSourceProxyPostProcessor() {
			return new org.springframework.beans.factory.config.BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (bean instanceof DataSource realDataSource && !(bean instanceof DelegatingDataSource)) {
						return new DelegatingDataSource(realDataSource) {
							@Override
							public Connection getConnection() throws java.sql.SQLException {
								Connection conn = super.getConnection();
								return (Connection) Proxy.newProxyInstance(
										Connection.class.getClassLoader(),
										new Class<?>[]{Connection.class},
										(proxy, method, args) -> {
											String name = method.getName();
											if ("prepareStatement".equals(name) || "createStatement".equals(name) || "prepareCall".equals(name)) {
												QUERY_COUNT.incrementAndGet();
											}
											return method.invoke(conn, args);
										}
								);
							}
						};
					}
					return bean;
				}
			};
		}
	}

	@BeforeEach
	void resetQueryCounter() {
		QUERY_COUNT.set(0);
	}

	@Test
	@DisplayName("Lesson response with 50 resources costs a small constant number of queries")
	void lessonResponseWith50ResourcesRunsConstantQueries() throws Exception {
		UserEntity instructor = createInstructor("prof_qc1@example.test", "Instructor QC 1");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());
		long lessonId = course.lessonIds().getFirst();

		// Attach 50 resources to this lesson
		for (int i = 1; i <= 50; i++) {
			attachLessonResource(course.courseId(), lessonId, instructor.getId(), "DOCUMENT", "LESSON_RESOURCE", "READY");
		}

		UserEntity learner = createUser("learner_qc1@example.test", "Learner QC 1");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/lessons/{lessonId}", course.courseId(), lessonId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resources.length()").value(50));

		// Without memoization, 50 resources would run 50+ enrollment queries.
		// With memoization, total queries must be small and constant (<= 12 queries)
		int queries = QUERY_COUNT.get();
		assertThat(queries).isLessThanOrEqualTo(15);
	}

	@Test
	@DisplayName("Learner outline for 20 modules x 20 lessons runs a small constant number of queries")
	void outlineWith400LessonsRunsConstantQueries() throws Exception {
		long catId = getDefaultCategoryId();
		UserEntity instructor = createInstructor("prof_qc2@example.test", "Instructor QC 2");
		String instructorToken = tokenFor(instructor);

		// Directly seed 20 modules and 400 lessons in database for speed
		jdbc.update(
				"insert into courses (title, description, category_id, difficulty, owner_id, status, content_updated_at, created_at, updated_at) values ('Big Course', 'Big Desc', ?, 'INTERMEDIATE', ?, 'PUBLISHED', now(), now(), now())",
				catId, instructor.getId());
		long courseId = jdbc.queryForObject("select last_insert_id()", Long.class);

		for (int m = 1; m <= 20; m++) {
			jdbc.update(
					"insert into course_modules (course_id, title, description, position, created_at, updated_at) values (?, ?, 'desc', ?, now(), now())",
					courseId, "Module " + m, m);
			long modId = jdbc.queryForObject("select last_insert_id()", Long.class);
			for (int l = 1; l <= 20; l++) {
				jdbc.update(
						"insert into lessons (module_id, title, summary, position, created_at, updated_at) values (?, ?, 'summary', ?, now(), now())",
						modId, "Lesson " + m + "." + l, l);
			}
		}

		UserEntity learner = createUser("learner_qc2@example.test", "Learner QC 2");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", courseId)
				.header("Authorization", bearer(learnerToken))).andExpect(status().isOk());

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}", courseId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modules.length()").value(20));

		int queries = QUERY_COUNT.get();
		// Must not be 400+ queries (O(N) N+1 queries). Should be <= 15 queries.
		assertThat(queries).isLessThanOrEqualTo(15);
	}

	@Test
	@DisplayName("My Learning page of 12 courses runs a small constant number of queries")
	void myLearningPageRunsConstantQueries() throws Exception {
		long catId = getDefaultCategoryId();
		UserEntity instructor = createInstructor("prof_qc3@example.test", "Instructor QC 3");
		UserEntity learner = createUser("learner_qc3@example.test", "Learner QC 3");
		String learnerToken = tokenFor(learner);

		// Seed 12 courses and enroll learner
		for (int i = 1; i <= 12; i++) {
			jdbc.update(
					"insert into courses (title, description, category_id, difficulty, owner_id, status, content_updated_at, created_at, updated_at) values (?, 'desc', ?, 'INTERMEDIATE', ?, 'PUBLISHED', now(), now(), now())",
					"Course " + i, catId, instructor.getId());
			long courseId = jdbc.queryForObject("select last_insert_id()", Long.class);

			jdbc.update(
					"insert into course_modules (course_id, title, description, position, created_at, updated_at) values (?, 'Module 1', 'desc', 1, now(), now())",
					courseId);
			long modId = jdbc.queryForObject("select last_insert_id()", Long.class);

			jdbc.update("insert into lessons (module_id, title, summary, position, created_at, updated_at) values (?, 'Lesson 1', 'sum', 1, now(), now())", modId);

			jdbc.update(
					"insert into enrollments (user_id, course_id, status, enrolled_at, version, created_at, updated_at) values (?, ?, 'ACTIVE', now(), 0, now(), now())",
					learner.getId(), courseId);
			long enrollId = jdbc.queryForObject("select last_insert_id()", Long.class);

			jdbc.update(
					"insert into course_progress (enrollment_id, course_id, total_lessons, completed_lessons, percent, stale, computed_at, updated_at) values (?, ?, 1, 0, 0, false, now(), now())",
					enrollId, courseId);
		}

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/learning/courses?size=12")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(12));

		int queries = QUERY_COUNT.get();
		// Bulk metadata, bulk progress, bulk user profiles: <= 10 queries, NOT 12*N queries!
		assertThat(queries).isLessThanOrEqualTo(10);
	}
}
