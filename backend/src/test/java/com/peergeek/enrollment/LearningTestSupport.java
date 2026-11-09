package com.peergeek.enrollment;

import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.discovery.CourseStatsRecorder;
import com.peergeek.file.ResourceAccessService;
import com.peergeek.file.ResourceDownloadService;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
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
public abstract class LearningTestSupport extends AbstractIntegrationTest {

	@Autowired protected MockMvc mockMvc;
	@Autowired protected JdbcTemplate jdbc;
	@Autowired protected ObjectMapper objectMapper;
	@Autowired protected UserRepository userRepository;
	@Autowired protected JwtService jwtService;
	@Autowired protected EnrollmentRepository enrollmentRepository;
	@Autowired protected LessonProgressRepository lessonProgressRepository;
	@Autowired protected CourseProgressRepository courseProgressRepository;
	@Autowired protected ProgressService progressService;
	@Autowired protected EnrollmentService enrollmentService;
	@Autowired protected LearningContentService learningContentService;
	@Autowired protected CourseStructureQueryService courseStructureQueryService;
	@Autowired protected ResourceAccessService resourceAccessService;
	@Autowired protected ResourceDownloadService resourceDownloadService;
	@Autowired protected CourseStatsRecorder courseStatsRecorder;

	@BeforeEach
	void cleanDatabase() {
		jdbc.update("delete from bookmarks");
		jdbc.update("delete from lesson_notes");
		jdbc.update("delete from notifications");
		jdbc.update("delete from community_reports");
		jdbc.update("delete from reviews");
		jdbc.update("delete from lesson_progress");
		jdbc.update("delete from course_progress");
		jdbc.update("delete from enrollments");
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
	}

	@AfterEach
	void cleanDatabaseAfter() {
		cleanDatabase();
	}

	protected UserEntity createUser(String email, String displayName) {
		UserEntity user = new UserEntity(email, "hashed-pass-1234", displayName);
		user.updateProfile(displayName, "Learner", "Learning about distributed software systems.");
		return userRepository.saveAndFlush(user);
	}

	protected UserEntity createInstructor(String email, String displayName) {
		UserEntity user = new UserEntity(email, "hashed-pass-1234", displayName);
		user.updateProfile(displayName, "Senior Professor", "Author and instructor of advanced systems.");
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

	protected CourseCreationResult createCourseWithModulesAndLessons(String instructorToken, int moduleCount, int lessonsPerModule) throws Exception {
		long categoryId = getDefaultCategoryId();
		MvcResult courseResult = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(objectMapper.createObjectNode()
								.put("title", "Computer Systems Architecture")
								.put("description", "A deep dive into system organization, memory, and performance.")
								.put("categoryId", categoryId)
								.put("difficulty", "INTERMEDIATE")
								.set("topics", objectMapper.createArrayNode().add("systems").add("architecture")).toString()))
				.andExpect(status().isCreated()).andReturn();

		long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();
		java.util.List<Long> lessonIds = new java.util.ArrayList<>();

		for (int m = 1; m <= moduleCount; m++) {
			MvcResult moduleResult = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
							.header("Authorization", bearer(instructorToken))
							.contentType("application/json")
							.content("{\"title\":\"Module " + m + "\",\"description\":\"Module description " + m + "\"}"))
					.andExpect(status().isCreated()).andReturn();

			long moduleId = objectMapper.readTree(moduleResult.getResponse().getContentAsString()).get("id").asLong();

			for (int l = 1; l <= lessonsPerModule; l++) {
				MvcResult lessonResult = mockMvc.perform(post("/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
								.header("Authorization", bearer(instructorToken))
								.contentType("application/json")
								.content("{\"title\":\"Lesson " + m + "." + l + "\",\"summary\":\"Summary for lesson " + m + "." + l + "\"}"))
						.andExpect(status().isCreated()).andReturn();

				long lessonId = objectMapper.readTree(lessonResult.getResponse().getContentAsString()).get("id").asLong();
				lessonIds.add(lessonId);

				JsonNode document = objectMapper.readTree("""
						{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Content for lesson"}]}]}
						""");
				var contentReq = objectMapper.createObjectNode();
				contentReq.set("document", document);
				contentReq.put("baseVersion", 0);
				mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
								.header("Authorization", bearer(instructorToken))
								.contentType("application/json")
								.content(contentReq.toString()))
						.andExpect(status().isOk());
			}
		}

		return new CourseCreationResult(courseId, lessonIds);
	}

	protected void publishCourse(String instructorToken, long courseId) throws Exception {
		MvcResult started = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/readiness/runs", courseId)
						.header("Authorization", bearer(instructorToken)))
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
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());
	}

	protected void unpublishCourse(String instructorToken, long courseId) throws Exception {
		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/unpublish", courseId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());
	}

	protected long attachLessonResource(long courseId, long lessonId, long userId, String type, String purpose, String status) {
		String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8);
		String filename = "sample-" + uniqueId + "." + ("DOCUMENT".equals(type) ? "pdf" : ("IMAGE".equals(type) ? "png" : "mp4"));
		String mime = "DOCUMENT".equals(type) ? "application/pdf" : ("IMAGE".equals(type) ? "image/png" : "video/mp4");
		jdbc.update("""
				insert into resources (course_id, lesson_id, uploaded_by, purpose, type, title, original_filename, object_key, mime_type, size_bytes, status, position, created_at, updated_at)
				values (?, ?, ?, ?, ?, 'Resource Title', ?, ?, ?, 4096, ?, 0, now(), now())
				""", courseId, lessonId, userId, purpose, type, filename, "resources/" + courseId + "/" + uniqueId + "-" + filename, mime, status);
		return jdbc.queryForObject("select id from resources where course_id = ? and lesson_id = ? order by id desc limit 1", Long.class, courseId, lessonId);
	}

	public record CourseCreationResult(long courseId, java.util.List<Long> lessonIds) {}
}
