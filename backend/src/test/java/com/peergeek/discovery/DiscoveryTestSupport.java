package com.peergeek.discovery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserRole;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class DiscoveryTestSupport extends AbstractIntegrationTest {

	@Autowired protected MockMvc mockMvc;
	@Autowired protected JdbcTemplate jdbc;
	@Autowired protected ObjectMapper objectMapper;
	@Autowired protected UserRepository userRepository;
	@Autowired protected JwtService jwtService;
	@Autowired protected PublicCourseIndexRepository indexRepository;
	@Autowired protected CoursePublicStatsRepository statsRepository;
	@Autowired protected DiscoveryCacheManager cacheManager;
	@Autowired protected IndexService indexService;
	@Autowired protected DirtyCourseFlusher dirtyFlusher;
	@Autowired protected DirtyCourseBuffer dirtyCourseBuffer;

	@BeforeEach
	void cleanDatabase() {
		jdbc.update("delete from course_public_stats");
		jdbc.update("delete from public_course_index");
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

		dirtyCourseBuffer.drain();
		cacheManager.evictAll();
	}

	@AfterEach
	void cleanDatabaseAfter() {
		cleanDatabase();
	}

	protected UserEntity createInstructor(String email, String displayName) {
		UserEntity user = new UserEntity(email, "hashed-pass-1234", displayName);
		user.updateProfile(displayName, "Senior Software Engineer", "Passionate about distributed systems and cloud architecture.");
		return userRepository.saveAndFlush(user);
	}

	protected UserEntity createAdmin(String email, String displayName) {
		UserEntity user = new UserEntity(email, "hashed-pass-1234", displayName);
		user.setRole(UserRole.ADMIN);
		return userRepository.saveAndFlush(user);
	}

	protected String tokenFor(UserEntity user) {
		return jwtService.issue(user);
	}

	protected String bearer(String token) {
		return "Bearer " + token;
	}

	protected long getDefaultCategoryId() {
		return jdbc.queryForObject(
				"select id from categories where active = true order by sort_order, name limit 1",
				Long.class);
	}

	protected long createReadyCourse(String token, String title, String description) throws Exception {
		long categoryId = getDefaultCategoryId();
		MvcResult courseResult = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(objectMapper.createObjectNode()
								.put("title", title)
								.put("description", description != null ? description : "Comprehensive course description passing readiness check standards.")
								.put("categoryId", categoryId)
								.put("difficulty", "INTERMEDIATE")
								.set("topics", objectMapper.createArrayNode().add("java").add("spring")).toString()))
				.andExpect(status().isCreated()).andReturn();

		long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult moduleResult = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Module One\",\"description\":\"First Module\"}"))
				.andExpect(status().isCreated()).andReturn();

		long moduleId = objectMapper.readTree(moduleResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult lessonResult = mockMvc.perform(post("/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Lesson One\",\"summary\":\"First Lesson\"}"))
				.andExpect(status().isCreated()).andReturn();

		long lessonId = objectMapper.readTree(lessonResult.getResponse().getContentAsString()).get("id").asLong();

		JsonNode document = objectMapper.readTree("""
				{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Protected lesson body text"}]}]}
				""");
		var contentReq = objectMapper.createObjectNode();
		contentReq.set("document", document);
		contentReq.put("baseVersion", 0);
		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(contentReq.toString()))
				.andExpect(status().isOk());

		return courseId;
	}

	protected void publishCourse(String token, long courseId) throws Exception {
		// Run readiness check
		MvcResult started = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/readiness/runs", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isAccepted()).andReturn();
		long runId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();

		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (System.nanoTime() < deadline) {
			String state = jdbc.queryForObject("select status from readiness_runs where id = ?", String.class, runId);
			if ("COMPLETED".equals(state)) break;
			if ("FAILED".equals(state)) throw new AssertionError("Readiness run failed for course " + courseId);
			Thread.sleep(50);
		}

		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/publish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk());
	}

	protected void unpublishCourse(String token, long courseId) throws Exception {
		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/unpublish", courseId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk());
	}

	protected long attachThumbnailResource(long courseId, long userId, String status) {
		jdbc.update("""
				insert into resources (course_id, uploaded_by, purpose, type, title, original_filename, object_key, mime_type, size_bytes, status, created_at, updated_at)
				values (?, ?, 'COURSE_THUMBNAIL', 'IMAGE', 'thumbnail.png', 'thumbnail.png', ?, 'image/png', 2048, ?, now(), now())
				""", courseId, userId, "courses/" + courseId + "/thumb.png", status);

		long resourceId = jdbc.queryForObject("select id from resources where course_id = ? and purpose = 'COURSE_THUMBNAIL'", Long.class, courseId);
		jdbc.update("update courses set thumbnail_resource_id = ? where id = ?", resourceId, courseId);
		return resourceId;
	}
}
