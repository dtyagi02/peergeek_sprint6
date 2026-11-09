package com.peergeek.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadinessRegressionIntegrationTest extends AbstractIntegrationTest {
	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	@Autowired private UserRepository users;
	@Autowired private JwtService jwt;

	@BeforeEach
	void clearData() {
		jdbc.update("delete from readiness_issues");
		jdbc.update("delete from readiness_runs");
		jdbc.update("delete from audit_logs");
		jdbc.update("delete from resources");
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
	@DisplayName("1 & 2: Complete course returns ready: true and 0 blocking issues")
	void completeCourseReturnsReadyResult() throws Exception {
		String token = createInstructorToken("complete-owner@example.test");
		long courseId = createReadyCourse(token, "Complete Course", true);

		long runId = startAndAwaitReadiness(token, courseId);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}", courseId, runId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.ready").value(true))
				.andExpect(jsonPath("$.blockingCount").value(0))
				.andExpect(jsonPath("$.issues").isEmpty());

		// GET latest endpoint also returns the same run
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/latest", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(runId))
				.andExpect(jsonPath("$.ready").value(true));
	}

	@Test
	@DisplayName("3: Course with no modules is detected as COURSE_MODULE_REQUIRED")
	void courseWithNoModulesDetected() throws Exception {
		String token = createInstructorToken("no-modules@example.test");
		long categoryId = getActiveCategoryId();
		MvcResult result = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(mapper.createObjectNode()
								.put("title", "No Modules Course")
								.put("description", "A valid course description that is long enough to avoid warnings.")
								.put("categoryId", categoryId)
								.put("difficulty", "BEGINNER")
								.set("topics", mapper.createArrayNode().add("java")).toString()))
				.andExpect(status().isCreated()).andReturn();
		long courseId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

		long runId = startAndAwaitReadiness(token, courseId);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}", courseId, runId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.ready").value(false))
				.andExpect(jsonPath("$.blockingCount").value(1))
				.andExpect(jsonPath("$.issues[0].code").value("COURSE_MODULE_REQUIRED"))
				.andExpect(jsonPath("$.issues[0].ruleCode").value("COURSE_MODULE_REQUIRED"))
				.andExpect(jsonPath("$.issues[0].scope").value("COURSE"));
	}

	@Test
	@DisplayName("4 & 5: Empty module without lessons is detected with module context")
	void emptyModuleWithoutLessonsDetected() throws Exception {
		String token = createInstructorToken("empty-module@example.test");
		long categoryId = getActiveCategoryId();
		MvcResult courseRes = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(mapper.createObjectNode()
								.put("title", "Empty Module Course")
								.put("description", "A valid course description that is long enough to avoid warnings.")
								.put("categoryId", categoryId)
								.put("difficulty", "BEGINNER")
								.set("topics", mapper.createArrayNode().add("security")).toString()))
				.andExpect(status().isCreated()).andReturn();
		long courseId = mapper.readTree(courseRes.getResponse().getContentAsString()).get("id").asLong();

		MvcResult modRes = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Spring Security\",\"description\":\"Module without lessons\"}"))
				.andExpect(status().isCreated()).andReturn();
		long moduleId = mapper.readTree(modRes.getResponse().getContentAsString()).get("id").asLong();

		long runId = startAndAwaitReadiness(token, courseId);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}", courseId, runId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ready").value(false))
				.andExpect(jsonPath("$.blockingCount").value(1))
				.andExpect(jsonPath("$.issues[0].code").value("MODULE_LESSON_REQUIRED"))
				.andExpect(jsonPath("$.issues[0].moduleId").value(moduleId))
				.andExpect(jsonPath("$.issues[0].moduleTitle").value("Spring Security"))
				.andExpect(jsonPath("$.issues[0].message").value("Module 'Spring Security' has no lessons."));
	}

	@Test
	@DisplayName("6: Lesson missing required content is detected with lesson context")
	void lessonMissingRequiredContentDetected() throws Exception {
		String token = createInstructorToken("empty-lesson@example.test");
		long courseId = createReadyCourse(token, "Lesson Content Missing", false);

		long runId = startAndAwaitReadiness(token, courseId);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}", courseId, runId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ready").value(false))
				.andExpect(jsonPath("$.blockingCount").value(1))
				.andExpect(jsonPath("$.issues[0].code").value("LESSON_CONTENT_REQUIRED"))
				.andExpect(jsonPath("$.issues[0].scope").value("LESSON"))
				.andExpect(jsonPath("$.issues[0].lessonId").isNumber())
				.andExpect(jsonPath("$.issues[0].lessonTitle").value("JWT Authentication"))
				.andExpect(jsonPath("$.issues[0].message").value("Lesson 'JWT Authentication' has no required authored content."));
	}

	@Test
	@DisplayName("7: Course with multiple issues returns all applicable issues")
	void multipleIssuesReturned() throws Exception {
		String token = createInstructorToken("multi-issues@example.test");
		long categoryId = getActiveCategoryId();
		MvcResult courseRes = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(mapper.createObjectNode()
								.put("title", "Multi Issues Course")
								.put("description", "A valid course description that is long enough to avoid warnings.")
								.put("categoryId", categoryId)
								.put("difficulty", "BEGINNER")
								.set("topics", mapper.createArrayNode().add("java")).toString()))
				.andExpect(status().isCreated()).andReturn();
		long courseId = mapper.readTree(courseRes.getResponse().getContentAsString()).get("id").asLong();

		// Module 1 with empty lesson
		MvcResult mod1 = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Module One\",\"description\":\"Has empty lesson\"}"))
				.andExpect(status().isCreated()).andReturn();
		long mod1Id = mapper.readTree(mod1.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/v1/instructor/modules/{moduleId}/lessons", mod1Id)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Lesson without content\",\"summary\":\"Summary\"}"))
				.andExpect(status().isCreated());

		// Module 2 with no lessons
		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Module Two\",\"description\":\"Has no lessons\"}"))
				.andExpect(status().isCreated());

		long runId = startAndAwaitReadiness(token, courseId);

		MvcResult res = mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}", courseId, runId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ready").value(false))
				.andExpect(jsonPath("$.blockingCount").value(2))
				.andReturn();

		JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
		assertThat(json.get("issues")).hasSize(2);
		assertThat(json.at("/issues/0/code").asText()).isIn("LESSON_CONTENT_REQUIRED", "MODULE_LESSON_REQUIRED");
		assertThat(json.at("/issues/1/code").asText()).isIn("LESSON_CONTENT_REQUIRED", "MODULE_LESSON_REQUIRED");
	}

	@Test
	@DisplayName("9: Unauthorized instructor cannot inspect another instructor's course")
	void unauthorizedInstructorCannotInspectCourse() throws Exception {
		String ownerToken = createInstructorToken("owner1@example.test");
		String otherToken = createInstructorToken("other1@example.test");
		long courseId = createReadyCourse(ownerToken, "Owner Course", true);
		long runId = startAndAwaitReadiness(ownerToken, courseId);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}", courseId, runId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/readiness/runs", courseId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("13: Unready course cannot be published")
	void unreadyCourseCannotBePublished() throws Exception {
		String token = createInstructorToken("unready-publish@example.test");
		long courseId = createReadyCourse(token, "Unready Course", false); // lesson missing content
		long runId = startAndAwaitReadiness(token, courseId);

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("The latest readiness check contains blocking issues."));
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

	private long getActiveCategoryId() {
		return jdbc.queryForObject(
				"select id from categories where active = true order by sort_order, name limit 1",
				Long.class);
	}

	private long createReadyCourse(String token, String title, boolean withLessonContent) throws Exception {
		long categoryId = getActiveCategoryId();
		MvcResult courseResult = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(mapper.createObjectNode().put("title", title)
								.put("description", "A course description with enough detail to pass the readiness check without any warnings.")
								.put("categoryId", categoryId).put("difficulty", "BEGINNER")
								.set("topics", mapper.createArrayNode().add("testing")).toString()))
				.andExpect(status().isCreated()).andReturn();
		long courseId = mapper.readTree(courseResult.getResponse().getContentAsString())
				.get("id").asLong();
		MvcResult moduleResult = mockMvc.perform(post(
						"/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Module One\",\"description\":\"Module description\"}"))
				.andExpect(status().isCreated()).andReturn();
		long moduleId = mapper.readTree(moduleResult.getResponse().getContentAsString())
				.get("id").asLong();
		MvcResult lessonResult = mockMvc.perform(post(
						"/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"JWT Authentication\",\"summary\":\"Lesson summary\"}"))
				.andExpect(status().isCreated()).andReturn();
		long lessonId = mapper.readTree(lessonResult.getResponse().getContentAsString())
				.get("id").asLong();
		if (withLessonContent) {
			JsonNode document = mapper.readTree("""
					{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Authored lesson text that is valid and complete"}]}]}
					""");
			var contentRequest = mapper.createObjectNode();
			contentRequest.set("document", document);
			contentRequest.put("baseVersion", 0);
			mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
							.header("Authorization", bearer(token))
							.contentType("application/json")
							.content(contentRequest.toString()))
					.andExpect(status().isOk());
		}
		return courseId;
	}

	private String createInstructorToken(String email) throws Exception {
		UserEntity user = users.saveAndFlush(new UserEntity(email, "test-password-hash", "Instructor User"));
		return jwt.issue(user);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
