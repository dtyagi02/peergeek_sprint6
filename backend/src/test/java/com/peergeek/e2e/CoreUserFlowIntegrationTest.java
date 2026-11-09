package com.peergeek.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoreUserFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	private static String instructorToken;
	private static String learner1Token;
	private static String learner2Token;
	private static long courseId;
	private static long moduleId;
	private static long lessonId;
	private static long assignmentId;
	private static long submissionId;
	private static long notificationId;

	@Test
	@Order(1)
	@DisplayName("Step 1: User Registration & Login — Register Instructor and Learners")
	void step01_registerAndLogin() throws Exception {
		// 1. Register Instructor
		String instructorRegister = """
				{
					"email": "core-instructor@example.test",
					"password": "Password123!",
					"displayName": "Core Instructor"
				}
				""";
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(instructorRegister))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verificationRequired").value(true));

		jdbc.update("update users set email_verified = true where email = ?", "core-instructor@example.test");

		String instLogin = """
				{
					"email": "core-instructor@example.test",
					"password": "Password123!"
				}
				""";
		String instRes = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(instLogin))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode instJson = objectMapper.readTree(instRes);
		instructorToken = instJson.get("accessToken").asText();
		assertThat(instructorToken).isNotBlank();

		// 2. Register Learner 1
		String learner1Register = """
				{
					"email": "core-learner1@example.test",
					"password": "Password123!",
					"displayName": "Core Learner One"
				}
				""";
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(learner1Register))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verificationRequired").value(true));

		jdbc.update("update users set email_verified = true where email = ?", "core-learner1@example.test");

		String l1Login = """
				{
					"email": "core-learner1@example.test",
					"password": "Password123!"
				}
				""";
		String l1Res = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(l1Login))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode l1Json = objectMapper.readTree(l1Res);
		learner1Token = l1Json.get("accessToken").asText();
		assertThat(learner1Token).isNotBlank();

		// 3. Register Learner 2
		String learner2Register = """
				{
					"email": "core-learner2@example.test",
					"password": "Password123!",
					"displayName": "Core Learner Two"
				}
				""";
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(learner2Register))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verificationRequired").value(true));

		jdbc.update("update users set email_verified = true where email = ?", "core-learner2@example.test");

		String l2Login = """
				{
					"email": "core-learner2@example.test",
					"password": "Password123!"
				}
				""";
		String l2Res = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(l2Login))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode l2Json = objectMapper.readTree(l2Res);
		learner2Token = l2Json.get("accessToken").asText();
		assertThat(learner2Token).isNotBlank();

		// 4. Verify Login flow
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(l1Login))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists());
	}

	@Test
	@Order(2)
	@DisplayName("Step 2: Course Authoring — Instructor creates course, modules, lessons, and assignments")
	void step02_instructorCreatesCourseStructure() throws Exception {
		// 1. Create Course Draft
		String createCourse = """
				{
					"title": "Mastering Enterprise Java & Spring Boot",
					"description": "Comprehensive production guide from foundation to release with deep architectural patterns.",
					"categoryId": 1,
					"difficulty": "ADVANCED",
					"topics": ["java", "spring-boot", "mysql", "redis"]
				}
				""";
		String courseRes = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createCourse))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		courseId = objectMapper.readTree(courseRes).get("id").asLong();
		assertThat(courseId).isPositive();

		// 2. Add Module (POST /api/v1/instructor/courses/{courseId}/modules takes title, description)
		String createModule = """
				{
					"title": "Module 1: Production Monolith Fundamentals",
					"description": "Clean modular monoliths with domain boundaries."
				}
				""";
		String modRes = mockMvc.perform(post("/api/v1/instructor/courses/" + courseId + "/modules")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createModule))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		moduleId = objectMapper.readTree(modRes).get("id").asLong();

		// 3. Add Lesson (POST /api/v1/instructor/modules/{moduleId}/lessons takes title, summary)
		String createLesson = """
				{
					"title": "Lesson 1: Resilient Architecture and Safe Cache Patterns",
					"summary": "Building resilient services with graceful Redis degradation."
				}
				""";
		String lessonRes = mockMvc.perform(post("/api/v1/instructor/modules/" + moduleId + "/lessons")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createLesson))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		lessonId = objectMapper.readTree(lessonRes).get("id").asLong();

		// 4. Update Lesson Content with TipTap JSON (PUT /api/v1/instructor/lessons/{lessonId}/content takes document, baseVersion)
		String updateContent = """
				{
					"document": {
						"type": "doc",
						"content": [
							{
								"type": "paragraph",
								"content": [
									{
										"type": "text",
										"text": "Welcome to enterprise Spring Boot architecture. In this lesson we master safe caching."
									}
								]
							}
						]
					},
					"baseVersion": 0
				}
				""";
		mockMvc.perform(put("/api/v1/instructor/lessons/" + lessonId + "/content")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateContent))
				.andExpect(status().isOk());

		// 5. Add Assignment
		String createAssignment = """
				{
					"title": "Assignment 1: Architecture Review & Safe Cache Design",
					"instructions": "Implement a fallback cache strategy and submit your project answers.",
					"kind": "PROJECT",
					"maxPoints": 100.0,
					"allowText": true,
					"allowFiles": false,
					"moduleId": """ + moduleId + """
				}
				""";
		String assignRes = mockMvc.perform(post("/api/v1/instructor/courses/" + courseId + "/assignments")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createAssignment))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assignmentId = objectMapper.readTree(assignRes).get("id").asLong();

		// 6. Publish Assignment
		mockMvc.perform(post("/api/v1/instructor/assignments/" + assignmentId + "/publish")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	@Test
	@Order(3)
	@DisplayName("Step 3: Readiness Validation & Publishing — Instructor validates and publishes course")
	void step03_readinessValidationAndPublishing() throws Exception {
		// 1. Run readiness check (returns 202 ACCEPTED)
		String readinessRes = mockMvc.perform(post("/api/v1/instructor/courses/" + courseId + "/readiness/runs")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();

		long runId = objectMapper.readTree(readinessRes).get("id").asLong();

		// Wait for readiness check to complete
		boolean completed = false;
		for (int i = 0; i < 20; i++) {
			String pollRes = mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/readiness/runs/" + runId)
							.header("Authorization", "Bearer " + instructorToken))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();

			JsonNode runJson = objectMapper.readTree(pollRes);
			String status = runJson.get("status").asText();
			if ("COMPLETED".equals(status)) {
				completed = true;
				assertThat(runJson.get("blockingCount").asInt()).isZero();
				break;
			}
			Thread.sleep(100);
		}
		assertThat(completed).isTrue();

		// 2. Publish Course
		mockMvc.perform(post("/api/v1/instructor/courses/" + courseId + "/publish")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	@Test
	@Order(4)
	@DisplayName("Step 4: Course Discovery & Preview — Learner discovers and inspects published course")
	void step04_learnerDiscoversAndPreviewsCourse() throws Exception {
		// 1. Public catalog search
		mockMvc.perform(get("/api/v1/public/courses?q=Mastering"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].title").value("Mastering Enterprise Java & Spring Boot"));

		// 2. Category filtering
		mockMvc.perform(get("/api/v1/public/courses?categoryId=1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());

		// 3. Public Course Preview
		mockMvc.perform(get("/api/v1/public/courses/" + courseId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Mastering Enterprise Java & Spring Boot"))
				.andExpect(jsonPath("$.outline[0].moduleTitle").value("Module 1: Production Monolith Fundamentals"));
	}

	@Test
	@Order(5)
	@DisplayName("Step 5: Enrollment — Learner 1 enrolls in the course")
	void step05_learnerEnrolls() throws Exception {
		// 1. Enroll
		mockMvc.perform(post("/api/v1/learning/courses/" + courseId + "/enroll")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		// 2. Verify enrollment status
		mockMvc.perform(get("/api/v1/learning/courses/" + courseId + "/enrollment")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		// 3. Verify enrolled courses listing
		mockMvc.perform(get("/api/v1/learning/courses")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].courseId").value(courseId));
	}

	@Test
	@Order(6)
	@DisplayName("Step 6: Course Playback & Progress Tracking — Learner completes lesson and marks progress")
	void step06_learningAndProgress() throws Exception {
		// 1. Get continue lesson
		mockMvc.perform(get("/api/v1/learning/courses/" + courseId + "/continue")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonId").value(lessonId));

		// 2. Load lesson content in course player
		mockMvc.perform(get("/api/v1/learning/courses/" + courseId + "/lessons/" + lessonId)
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lesson.title").value("Lesson 1: Resilient Architecture and Safe Cache Patterns"));

		// 3. Mark lesson complete via PUT completion endpoint
		String completionBody = """
				{
					"completed": true
				}
				""";
		mockMvc.perform(put("/api/v1/learning/courses/" + courseId + "/lessons/" + lessonId + "/completion")
						.header("Authorization", "Bearer " + learner1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(completionBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.percent").value(100));

		// 4. Verify progress calculation
		mockMvc.perform(get("/api/v1/learning/courses/" + courseId)
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.progress.percent").value(100));
	}

	@Test
	@Order(7)
	@DisplayName("Step 7: Learner Extras — Course Bookmarks & Private Notes")
	void step07_bookmarksAndNotes() throws Exception {
		// 1. Create course bookmark (returns 200 OK)
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/bookmarks")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetType").value("COURSE"));

		// 2. Verify bookmark in list
		mockMvc.perform(get("/api/v1/bookmarks")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].courseId").value(courseId));

		// 3. Write private lesson note
		String noteBody = """
				{
					"content": "Key takeaways: Always use SafeCache with in-memory fallback for high availability."
				}
				""";
		mockMvc.perform(put("/api/v1/courses/" + courseId + "/lessons/" + lessonId + "/notes")
						.header("Authorization", "Bearer " + learner1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(noteBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Key takeaways: Always use SafeCache with in-memory fallback for high availability."));

		// 4. Verify note privacy: Learner 2 cannot read Learner 1's note (Learner 2 not enrolled -> 403 Forbidden)
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/lessons/" + lessonId + "/notes")
						.header("Authorization", "Bearer " + learner2Token))
				.andExpect(status().isForbidden());
	}

	@Test
	@Order(8)
	@DisplayName("Step 8: Assignments & Submission — Learner takes assignment and submits answer")
	void step08_learnerSubmitsAssignment() throws Exception {
		// 1. View published assignments
		mockMvc.perform(get("/api/v1/learning/courses/" + courseId + "/assignments")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(assignmentId));

		// 2. Start attempt
		String startRes = mockMvc.perform(post("/api/v1/learning/assignments/" + assignmentId + "/start")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andReturn().getResponse().getContentAsString();

		long attemptVersion = objectMapper.readTree(startRes).get("version").asLong();

		// 3. Save draft
		String draft = String.format("""
				{
					"version": %d,
					"textAnswer": "Draft answer: SafeCache catches Redis exceptions and delegates to supplier."
				}
				""", attemptVersion);
		String draftRes = mockMvc.perform(put("/api/v1/learning/assignments/" + assignmentId + "/draft")
						.header("Authorization", "Bearer " + learner1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draft))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		long draftVersion = objectMapper.readTree(draftRes).get("version").asLong();

		// 4. Submit assignment
		String submit = String.format("""
				{
					"version": %d
				}
				""", draftVersion);
		String subRes = mockMvc.perform(post("/api/v1/learning/assignments/" + assignmentId + "/submit")
						.header("Authorization", "Bearer " + learner1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(submit))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andReturn().getResponse().getContentAsString();

		submissionId = objectMapper.readTree(subRes).get("submissionId").asLong();
		assertThat(submissionId).isPositive();
	}

	@Test
	@Order(9)
	@DisplayName("Step 9: Instructor Review & Grading — Instructor grades submission and provides feedback")
	void step09_instructorGradesSubmission() throws Exception {
		// 1. Review submissions queue
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/submissions?assignmentId=" + assignmentId)
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].submissionId").value(submissionId))
				.andExpect(jsonPath("$.content[0].status").value("SUBMITTED"));

		// 2. Fetch submission detail to get optimistic lock version
		String subDetailRes = mockMvc.perform(get("/api/v1/instructor/submissions/" + submissionId)
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		long subVersion = objectMapper.readTree(subDetailRes).get("version").asLong();

		// 3. Grade submission
		String gradeBody = String.format("""
				{
					"version": %d,
					"marks": 95.0,
					"overallFeedback": "Excellent architectural understanding and clean design pattern implementation!"
				}
				""", subVersion);
		mockMvc.perform(put("/api/v1/instructor/submissions/" + submissionId + "/grade")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(gradeBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(95.0));

		// 4. Learner views graded submission
		mockMvc.perform(get("/api/v1/learning/assignments/" + assignmentId + "/submission")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("GRADED"))
				.andExpect(jsonPath("$.totalPoints").value(95.0))
				.andExpect(jsonPath("$.overallFeedback").value("Excellent architectural understanding and clean design pattern implementation!"));
	}

	@Test
	@Order(10)
	@DisplayName("Step 10: Notifications — Learner receives and acknowledges grade notification")
	void step10_notificationsFlow() throws Exception {
		// 1. Verify notification received
		String notifRes = mockMvc.perform(get("/api/v1/notifications")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("SUBMISSION_GRADED"))
				.andReturn().getResponse().getContentAsString();

		JsonNode notifJson = objectMapper.readTree(notifRes);
		notificationId = notifJson.get("content").get(0).get("id").asLong();

		// 2. Verify unread count > 0
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(1));

		// 3. Mark notification as read
		mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
						.header("Authorization", "Bearer " + learner1Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(notificationId));
	}

	@Test
	@Order(11)
	@DisplayName("Step 11: Reviews & Reporting — Learner submits review and files community report")
	void step11_reviewsAndCommunityReports() throws Exception {
		// 1. Submit review
		String reviewBody = """
				{
					"rating": 5,
					"reviewText": "Outstanding enterprise monolith course! Best technical content on the platform."
				}
				""";
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/reviews")
						.header("Authorization", "Bearer " + learner1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(5));

		// 2. Submit community report
		String reportBody = """
				{
					"category": "BROKEN_RESOURCE",
					"details": "Minor formatting issue in code snippet block."
				}
				""";
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/reports")
						.header("Authorization", "Bearer " + learner1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(reportBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").value("BROKEN_RESOURCE"));

		// 3. Instructor inspects community reports
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/reports")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].category").value("BROKEN_RESOURCE"));
	}

	@Test
	@Order(12)
	@DisplayName("Step 12: Instructor Analytics — Authoritative metrics reflect real student activity")
	void step12_instructorAnalyticsVerification() throws Exception {
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/analytics")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEnrollments").value(1))
				.andExpect(jsonPath("$.activeLearners").value(1))
				.andExpect(jsonPath("$.completionRate").value(100.0))
				.andExpect(jsonPath("$.averageAssessmentScore").value(95.0))
				.andExpect(jsonPath("$.averageRating").value(5.0))
				.andExpect(jsonPath("$.lessonDropOff[0].completedCount").value(1))
				.andExpect(jsonPath("$.assignmentStats[0].submittedCount").value(1));
	}

	@Test
	@Order(13)
	@DisplayName("Step 13: Second Learner Enrollment — Second user discovers and enrolls in the live course")
	void step13_secondLearnerEnrolls() throws Exception {
		// Learner 2 discovers and enrolls
		mockMvc.perform(post("/api/v1/learning/courses/" + courseId + "/enroll")
						.header("Authorization", "Bearer " + learner2Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		// Verify analytics reflects 2 enrollments
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/analytics")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEnrollments").value(2))
				.andExpect(jsonPath("$.activeLearners").value(2));
	}
}
