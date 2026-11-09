package com.peergeek.assignment;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AssignmentQueryCountGuardIntegrationTest.QueryCountingConfiguration.class)
class AssignmentQueryCountGuardIntegrationTest extends AssignmentTestSupport {

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
	@DisplayName("Instructor assignments list executes constant queries without N+1 per assignment")
	void instructorAssignmentsListConstantQueries() throws Exception {
		UserEntity instructor = createInstructor("prof.qc@example.test", "Prof QC");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		for (int i = 1; i <= 5; i++) {
			createAssignment(instructorToken, courseId, "Assignment " + i, AssignmentKind.QUESTIONS);
		}

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());

		int queries = QUERY_COUNT.get();
		// Metadata check + paginated assignments query + count query + single grouped status count query
		assertThat(queries).isLessThanOrEqualTo(8);
	}

	@Test
	@DisplayName("Instructor submission queue executes constant queries without N+1 per submission")
	void instructorSubmissionQueueConstantQueries() throws Exception {
		UserEntity instructor = createInstructor("prof.qsub@example.test", "Prof QSub");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Queue Quiz", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "What is 1+1?", 5, false, List.of(
				buildMcqOption("o1", "2", true),
				buildMcqOption("o2", "3", false)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));
		publishAssignment(instructorToken, assignmentId);

		for (int i = 1; i <= 3; i++) {
			UserEntity learner = createUser("learner" + i + "@example.test", "Learner " + i);
			String lToken = tokenFor(learner);
			enrollLearner(lToken, courseId);
			mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
							.header("Authorization", bearer(lToken)))
					.andExpect(status().isOk());
		}

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/submissions", courseId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());

		int queries = QUERY_COUNT.get();
		// Metadata check + assignment ids fetch + paginated submissions + count + bulk user lookup + bulk assignment lookup
		assertThat(queries).isLessThanOrEqualTo(10);
	}

	@Test
	@DisplayName("Learner course assignments list executes constant queries")
	void learnerCourseAssignmentsConstantQueries() throws Exception {
		UserEntity instructor = createInstructor("prof.ql@example.test", "Prof QL");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		for (int i = 1; i <= 3; i++) {
			long aid = createAssignment(instructorToken, courseId, "Assignment " + i, AssignmentKind.QUESTIONS);
			var q = buildMcqQuestion(1, "Q?", 5, false, List.of(
					buildMcqOption("o1", "A", true),
					buildMcqOption("o2", "B", false)
			));
			replaceQuestions(instructorToken, aid, List.of(q));
			publishAssignment(instructorToken, aid);
		}

		UserEntity learner = createUser("learner.ql@example.test", "Learner QL");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/learning/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		int queries = QUERY_COUNT.get();
		assertThat(queries).isLessThanOrEqualTo(8);
	}
}
