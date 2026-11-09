package com.peergeek.assignment;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.course.CourseRepository;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.security.JwtService;
import com.peergeek.submission.SubmissionAnswerRepository;
import com.peergeek.submission.SubmissionFileRepository;
import com.peergeek.submission.SubmissionRepository;
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
public abstract class AssignmentTestSupport extends AbstractIntegrationTest {

	@Autowired protected MockMvc mockMvc;
	@Autowired protected JdbcTemplate jdbc;
	@Autowired protected ObjectMapper objectMapper;
	@Autowired protected UserRepository userRepository;
	@Autowired protected JwtService jwtService;
	@Autowired protected CourseRepository courseRepository;
	@Autowired protected EnrollmentRepository enrollmentRepository;
	@Autowired protected AssignmentRepository assignmentRepository;
	@Autowired protected AssignmentQuestionRepository questionRepository;
	@Autowired protected SubmissionRepository submissionRepository;
	@Autowired protected SubmissionAnswerRepository answerRepository;
	@Autowired protected SubmissionFileRepository fileRepository;
	@Autowired protected Clock clock;

	@BeforeEach
	void cleanDatabase() {
		jdbc.update("delete from notifications");
		jdbc.update("delete from community_reports");
		jdbc.update("delete from reviews");
		jdbc.update("delete from submission_files");
		jdbc.update("delete from submission_answers");
		jdbc.update("delete from submissions");
		jdbc.update("delete from assignment_questions");
		jdbc.update("delete from assignments");
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
		user.updateProfile(displayName, "Learner", "Learning distributed systems.");
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

	protected long createCourseWithModulesAndLessons(String instructorToken, int moduleCount, int lessonsPerModule) throws Exception {
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

		return courseId;
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

	protected void enrollLearner(String learnerToken, long courseId) throws Exception {
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", courseId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());
	}

	protected long createAssignment(String instructorToken, long courseId, String title, AssignmentKind kind) throws Exception {
		ObjectNode req = objectMapper.createObjectNode();
		req.put("title", title);
		req.put("kind", kind.name());
		req.put("instructions", "Complete this assignment carefully according to instructions.");
		req.put("allowLate", true);
		req.put("allowText", true);
		if (kind == AssignmentKind.PROJECT) {
			req.put("maxPoints", 100);
			req.put("allowFiles", true);
		}

		MvcResult res = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/assignments", courseId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(req.toString()))
				.andExpect(status().isCreated()).andReturn();

		return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
	}

	protected long createHistoricalProjectAssignment(long courseId, String title, Long instructorId) {
		AssignmentEntity assignment = new AssignmentEntity(
				courseId,
				null,
				null,
				AssignmentKind.PROJECT,
				title,
				"Historical project assignment instructions.",
				java.math.BigDecimal.valueOf(100),
				instructorId);
		assignment.setStatus(AssignmentStatus.PUBLISHED);
		assignment.setAllowFiles(true);
		assignment.setAllowText(true);
		assignment.setAllowLate(true);
		assignment.setPublishedAt(java.time.Instant.now());
		return assignmentRepository.save(assignment).getId();
	}

	protected ObjectNode buildMcqOption(String id, String text, boolean correct) {
		ObjectNode opt = objectMapper.createObjectNode();
		opt.put("id", id);
		opt.put("text", text);
		opt.put("correct", correct);
		return opt;
	}

	protected ObjectNode buildMcqQuestion(int orderIndex, String prompt, int points, boolean multi, List<ObjectNode> options) {
		ObjectNode q = objectMapper.createObjectNode();
		q.put("type", multi ? "MCQ_MULTI" : "MCQ_SINGLE");
		q.put("prompt", prompt);
		q.put("points", points);
		ArrayNode opts = q.putArray("options");
		options.forEach(opts::add);
		return q;
	}

	protected ObjectNode buildProjectQuestion(int orderIndex, String prompt, int points, int maxFiles, List<String> allowedTypes) {
		ObjectNode q = objectMapper.createObjectNode();
		q.put("type", "DESCRIPTIVE");
		q.put("prompt", prompt);
		q.put("points", points);
		return q;
	}

	protected void replaceQuestions(String instructorToken, long assignmentId, List<ObjectNode> questions) throws Exception {
		ArrayNode arr = objectMapper.createArrayNode();
		questions.forEach(arr::add);

		mockMvc.perform(put("/api/v1/instructor/assignments/{assignmentId}/questions", assignmentId)
						.header("Authorization", bearer(instructorToken))
						.contentType("application/json")
						.content(arr.toString()))
				.andExpect(status().isOk());
	}

	protected void publishAssignment(String instructorToken, long assignmentId) throws Exception {
		mockMvc.perform(post("/api/v1/instructor/assignments/{assignmentId}/publish", assignmentId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk());
	}
}
