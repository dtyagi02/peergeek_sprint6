package com.peergeek.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.course.CoursePublishedEvent;
import com.peergeek.course.CourseUnpublishedEvent;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReadinessPublishingIntegrationTest.EventListenerConfiguration.class)
class ReadinessPublishingIntegrationTest extends AbstractIntegrationTest {
	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	@Autowired private UserRepository users;
	@Autowired private JwtService jwt;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private CourseEventListener courseEvents;

	@BeforeEach
	void clearData() {
		courseEvents.clear();
		jdbc.update("delete from readiness_issues");
		jdbc.update("delete from readiness_runs");
		jdbc.update("delete from audit_logs");
		jdbc.update("delete from lesson_contents");
		jdbc.update("delete from lessons");
		jdbc.update("delete from course_modules");
		jdbc.update("delete from course_topics");
		jdbc.update("delete from courses");
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from password_reset_otps");
		jdbc.update("delete from users");
	}

	@Test
	void ownerCanRunReadinessPublishAndLoadInstructorPreview() throws Exception {
		String token = createInstructorToken("phase4-owner@example.test");
		long courseId = createReadyCourse(token, "Phase Four");

		MvcResult started = mockMvc.perform(post(
						"/api/v1/instructor/courses/{courseId}/readiness/runs", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.id").isNumber())
				.andReturn();
		long runId = mapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();
		awaitRun(runId);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}",
						courseId, runId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.blockingCount").value(0));

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/preview", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categoryName").isNotEmpty())
				.andExpect(jsonPath("$.modules[0].description").value("Module description"))
				.andExpect(jsonPath("$.modules[0].lessons[0].summary").value("Lesson summary"))
				.andExpect(jsonPath("$.modules[0].lessons[0].document.type").value("doc"));

		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'COURSE_PUBLISHED' and entity_id = ?",
				Integer.class, Long.toString(courseId))).isEqualTo(1);
	}

	@Test
	void changedCourseCannotUseAnOldReadinessReportToPublish() throws Exception {
		String token = createInstructorToken("stale-owner@example.test");
		long courseId = createReadyCourse(token, "Stale Report");
		long runId = startAndAwaitReadiness(token, courseId);
		assertThat(runId).isPositive();

		long version = jdbc.queryForObject("select version from courses where id = ?",
				Long.class, courseId);
		String patch = "{\"version\":" + version + ",\"title\":\"Changed after readiness\"}";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.patch("/api/v1/instructor/courses/{courseId}", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json").content(patch))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						"The course has changed since its latest readiness check. Run readiness again."));
	}

	@Test
	void publishAndUnpublishEventsAreDeliveredOnceAfterCommitAndNeverOnRollback() throws Exception {
		String token = createInstructorToken("events-owner@example.test");
		long courseId = createReadyCourse(token, "Course Events");
		startAndAwaitReadiness(token, courseId);
		TransactionTemplate transactions = new TransactionTemplate(transactionManager);

		transactions.executeWithoutResult(status -> {
			performPublish(token, courseId);
			assertThat(courseEvents.events()).isEmpty();
			status.setRollbackOnly();
		});
		assertThat(courseEvents.events()).isEmpty();
		assertThat(courseStatus(courseId)).isEqualTo("DRAFT");

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk());
		assertThat(courseEvents.events()).hasSize(1)
				.allSatisfy(event -> assertThat(event).isInstanceOf(CoursePublishedEvent.class));

		transactions.executeWithoutResult(status -> {
			performUnpublish(token, courseId);
			assertThat(courseEvents.events()).hasSize(1);
			status.setRollbackOnly();
		});
		assertThat(courseEvents.events()).hasSize(1);
		assertThat(courseStatus(courseId)).isEqualTo("PUBLISHED");

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/unpublish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk());
		List<Object> events = courseEvents.events();
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(CoursePublishedEvent.class);
		assertThat(events.get(1)).isInstanceOf(CourseUnpublishedEvent.class);
	}

	@Test
	void rejectedTransitionsDoNotPublishLifecycleEvents() throws Exception {
		String token = createInstructorToken("rejected-events-owner@example.test");
		long courseId = createReadyCourse(token, "Rejected Transitions");

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/unpublish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isConflict());

		assertThat(courseEvents.events()).isEmpty();
	}

	private void performPublish(String token, long courseId) {
		try {
			mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
							.header("Authorization", bearer(token)))
					.andExpect(status().isOk());
		} catch (Exception exception) {
			throw new AssertionError("Publishing within the test transaction failed.", exception);
		}
	}

	private void performUnpublish(String token, long courseId) {
		try {
			mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/unpublish", courseId)
							.header("Authorization", bearer(token)))
					.andExpect(status().isOk());
		} catch (Exception exception) {
			throw new AssertionError("Unpublishing within the test transaction failed.", exception);
		}
	}

	private String courseStatus(long courseId) {
		return jdbc.queryForObject("select status from courses where id = ?", String.class, courseId);
	}

	private long startAndAwaitReadiness(String token, long courseId) throws Exception {
		MvcResult started = mockMvc.perform(post(
						"/api/v1/instructor/courses/{courseId}/readiness/runs", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isAccepted()).andReturn();
		long id = mapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();
		awaitRun(id);
		return id;
	}

	private void awaitRun(long runId) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (System.nanoTime() < deadline) {
			String state = jdbc.queryForObject("select status from readiness_runs where id = ?",
					String.class, runId);
			if ("COMPLETED".equals(state)) return;
			if ("FAILED".equals(state)) throw new AssertionError("Readiness run failed: " + runId);
			Thread.sleep(50);
		}
		throw new AssertionError("Readiness run did not finish before timeout: " + runId);
	}

	private long createReadyCourse(String token, String title) throws Exception {
		long categoryId = jdbc.queryForObject(
				"select id from categories where active = true order by sort_order, name limit 1",
				Long.class);
		MvcResult courseResult = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(mapper.createObjectNode().put("title", title)
								.put("description", "A course description with enough detail to pass the readiness check.")
								.put("categoryId", categoryId).put("difficulty", "BEGINNER")
								.set("topics", mapper.createArrayNode().add("testing")).toString()))
				.andExpect(status().isCreated()).andReturn();
		long courseId = mapper.readTree(courseResult.getResponse().getContentAsString())
				.get("id").asLong();
		MvcResult moduleResult = mockMvc.perform(post(
						"/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Module\",\"description\":\"Module description\"}"))
				.andExpect(status().isCreated()).andReturn();
		long moduleId = mapper.readTree(moduleResult.getResponse().getContentAsString())
				.get("id").asLong();
		MvcResult lessonResult = mockMvc.perform(post(
						"/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Lesson\",\"summary\":\"Lesson summary\"}"))
				.andExpect(status().isCreated()).andReturn();
		long lessonId = mapper.readTree(lessonResult.getResponse().getContentAsString())
				.get("id").asLong();
		JsonNode document = mapper.readTree("""
				{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Authored lesson text"}]}]}
				""");
		var contentRequest = mapper.createObjectNode();
		contentRequest.set("document", document);
		contentRequest.put("baseVersion", 0);
		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(contentRequest.toString()))
				.andExpect(status().isOk());
		return courseId;
	}

	private String createInstructorToken(String email) throws Exception {
		UserEntity user = users.saveAndFlush(new UserEntity(email, "test-password-hash", "Phase Four"));
		return jwt.issue(user);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	@TestConfiguration
	static class EventListenerConfiguration {
		@Bean
		CourseEventListener courseEventListener() {
			return new CourseEventListener();
		}
	}

	static class CourseEventListener {
		private final CopyOnWriteArrayList<Object> received = new CopyOnWriteArrayList<>();

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		void onPublished(CoursePublishedEvent event) {
			received.add(event);
		}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		void onUnpublished(CourseUnpublishedEvent event) {
			received.add(event);
		}

		List<Object> events() {
			return List.copyOf(received);
		}

		void clear() {
			received.clear();
		}
	}
}
