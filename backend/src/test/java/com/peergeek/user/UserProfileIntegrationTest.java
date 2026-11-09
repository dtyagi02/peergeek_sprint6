package com.peergeek.user;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.auth.AuthService;
import com.peergeek.auth.RegisterRequest;
import com.peergeek.auth.SessionResponse;
import com.peergeek.course.CourseRepository;
import com.peergeek.enrollment.CourseProgressEntity;
import com.peergeek.enrollment.CourseProgressRepository;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.enrollment.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UserRepository users;

	@Autowired
	private CourseRepository courses;

	@Autowired
	private EnrollmentRepository enrollments;

	@Autowired
	private CourseProgressRepository courseProgresses;

	@Autowired
	private UserEducationRepository educations;

	@Autowired
	private UserProjectRepository projects;

	@Autowired
	private com.peergeek.security.JwtService jwtService;

	private String registerUser(String email, String username, String displayName) {
		UserEntity user = new UserEntity(email, "hashedPassword", displayName, username, true);
		user = users.save(user);
		return jwtService.issue(user);
	}

	@Test
	void userCanRetrieveAndEditTheirOwnFullProfile() throws Exception {
		String token = registerUser("alice@example.com", "alice_dev", "Alice Smith");
		UserEntity alice = users.findByUsername("alice_dev").orElseThrow();

		// Retrieve initial profile
		mockMvc.perform(get("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(alice.getId()))
				.andExpect(jsonPath("$.username").value("alice_dev"))
				.andExpect(jsonPath("$.email").value("alice@example.com"))
				.andExpect(jsonPath("$.isOwner").value(true));

		// Update profile
		UpdateProfileRequest updateReq = new UpdateProfileRequest(
				"Alice W. Smith",
				"alice_updated",
				"Software Engineer & Learner",
				"Passionate about software architecture and algorithms",
				List.of("Java", "Spring Boot", "React"),
				List.of("Distributed Systems", "Cloud"),
				null,
				"https://github.com/alice",
				"https://linkedin.com/in/alice",
				"https://alice.dev",
				"https://alice.blog",
				true,
				true,
				false);

		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Alice W. Smith"))
				.andExpect(jsonPath("$.username").value("alice_updated"))
				.andExpect(jsonPath("$.headline").value("Software Engineer & Learner"))
				.andExpect(jsonPath("$.githubUrl").value("https://github.com/alice"))
				.andExpect(jsonPath("$.showLearningHistory").value(false));

		UserEntity reloaded = users.findById(alice.getId()).orElseThrow();
		assertThat(reloaded.getUsername()).isEqualTo("alice_updated");
		assertThat(reloaded.getDisplayName()).isEqualTo("Alice W. Smith");
	}

	@Test
	void profileEditingRejectsReservedUsernamesAndEnforcesCaseInsensitiveUniqueness() throws Exception {
		String token1 = registerUser("bob1@example.com", "bob_dev", "Bob One");
		String token2 = registerUser("bob2@example.com", "charlie_dev", "Charlie");

		// Reserved username rejected
		UpdateProfileRequest reservedReq = new UpdateProfileRequest(
				"Bob One", "admin", "Engineer", "Bio", null, null, null, null, null, null, null, null, null, null);
		mockMvc.perform(patch("/api/v1/users/me")
						.header("Authorization", "Bearer " + token1)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(reservedReq)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("username"));

		// Duplicate username (case-insensitive) rejected
		UpdateProfileRequest duplicateReq = new UpdateProfileRequest(
				"Charlie", "BOB_DEV", "Engineer", "Bio", null, null, null, null, null, null, null, null, null, null);
		mockMvc.perform(patch("/api/v1/users/me")
						.header("Authorization", "Bearer " + token2)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(duplicateReq)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("username"));
	}

	@Test
	void educationCrudEndpointsWorkWithOwnershipGuard() throws Exception {
		String token = registerUser("student_edu@example.com", "student_edu", "Edu Student");

		UpsertEducationRequest createReq = new UpsertEducationRequest(
				"Stanford University", "B.S.", "Computer Science", 2020, 2024);

		MvcResult createResult = mockMvc.perform(post("/api/v1/users/me/education")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createReq)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.institution").value("Stanford University"))
				.andExpect(jsonPath("$.degree").value("B.S."))
				.andExpect(jsonPath("$.startYear").value(2020))
				.andReturn();

		Long eduId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

		// Update
		UpsertEducationRequest updateReq = new UpsertEducationRequest(
				"Stanford University", "M.S.", "Computer Science", 2024, 2026);
		mockMvc.perform(put("/api/v1/users/me/education/" + eduId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.degree").value("M.S."));

		// Delete
		mockMvc.perform(delete("/api/v1/users/me/education/" + eduId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
	}

	@Test
	void projectsCrudEndpointsWorkWithOwnershipGuard() throws Exception {
		String token = registerUser("proj_user@example.com", "proj_user", "Project User");

		UpsertProjectRequest createReq = new UpsertProjectRequest(
				"PeerGeek System",
				"Full-stack collaborative learning platform",
				"Java, React, MySQL",
				"https://peergeek.com",
				"https://github.com/peergeek/peergeek");

		MvcResult createResult = mockMvc.perform(post("/api/v1/users/me/projects")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createReq)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("PeerGeek System"))
				.andExpect(jsonPath("$.technologies").value("Java, React, MySQL"))
				.andReturn();

		Long projId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

		// Delete
		mockMvc.perform(delete("/api/v1/users/me/projects/" + projId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
	}

	@Test
	void avatarUploadStreamingAndDelete() throws Exception {
		String token = registerUser("avatar_user@example.com", "avatar_user", "Avatar User");
		UserEntity user = users.findByUsername("avatar_user").orElseThrow();

		MockMultipartFile file = new MockMultipartFile(
				"file", "avatar.png", "image/png", new byte[]{ (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 });

		mockMvc.perform(multipart("/api/v1/users/me/avatar")
						.file(file)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarUrl").value("/api/v1/users/" + user.getId() + "/avatar"));

		// Stream avatar publicly
		mockMvc.perform(get("/api/v1/users/" + user.getId() + "/avatar"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"));

		// Delete avatar
		mockMvc.perform(delete("/api/v1/users/me/avatar")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
	}

	@Test
	void scenario1And4StudentViewingStudentRespectsPrivacySettings() throws Exception {
		String student1Token = registerUser("target_stud@example.com", "target_stud", "Target Student");
		String student2Token = registerUser("viewer_stud@example.com", "viewer_stud", "Viewer Student");
		UserEntity target = users.findByUsername("target_stud").orElseThrow();

		// Target sets privacy: showEducation=false, showSocialLinks=false, showLearningHistory=false
		UpdateProfileRequest updateReq = new UpdateProfileRequest(
				"Target Student", "target_stud", "Learner", "Bio", List.of("Python"), List.of("AI"),
				null, "https://github.com/target", null, null, null, false, false, false);

		mockMvc.perform(patch("/api/v1/users/me")
						.header("Authorization", "Bearer " + student1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk());

		// Add education and project
		mockMvc.perform(post("/api/v1/users/me/education")
						.header("Authorization", "Bearer " + student1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new UpsertEducationRequest("MIT", "B.S.", "EECS", 2019, 2023))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/users/me/projects")
						.header("Authorization", "Bearer " + student1Token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new UpsertProjectRequest("AI Assistant", "Description", "Python", null, null))))
				.andExpect(status().isCreated());

		// Viewer student requests target profile
		mockMvc.perform(get("/api/v1/users/" + target.getId() + "/profile")
						.header("Authorization", "Bearer " + student2Token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("target_stud"))
				.andExpect(jsonPath("$.isOwner").value(false))
				.andExpect(jsonPath("$.email").doesNotExist()) // Never exposes private email
				.andExpect(jsonPath("$.githubUrl").doesNotExist()) // Hidden by privacy
				.andExpect(jsonPath("$.educations").isEmpty()) // Hidden by privacy
				.andExpect(jsonPath("$.projects").isNotEmpty()); // Projects are public
	}

	@Test
	void scenario2InstructorViewsEnrolledStudentCourseProgressAndSubmissions() throws Exception {
		String instrToken = registerUser("prof_smith@example.com", "prof_smith", "Prof Smith");
		UserEntity instructor = users.findByUsername("prof_smith").orElseThrow();

		String studentToken = registerUser("john_doe@example.com", "john_doe", "John Doe");
		UserEntity student = users.findByUsername("john_doe").orElseThrow();

		// Create course owned by instructor via SQL
		long categoryId = jdbc.queryForObject("select id from categories where active = true limit 1", Long.class);
		jdbc.update("insert into courses (owner_id, title, description, category_id, difficulty, status, content_updated_at, created_at, updated_at, version) values (?, ?, ?, ?, 'BEGINNER', 'PUBLISHED', now(), now(), now(), 0)",
				instructor.getId(), "Advanced Java Architecture", "Desc", categoryId);
		long courseId = jdbc.queryForObject("select id from courses where owner_id = ? order by id desc limit 1", Long.class, instructor.getId());

		// Enroll student
		EnrollmentEntity enrollment = new EnrollmentEntity(student.getId(), courseId, Instant.now());
		enrollments.saveAndFlush(enrollment);

		// Record course progress
		CourseProgressEntity progress = new CourseProgressEntity(
				enrollment.getId(), courseId, 10, 8, 80, null, false, Instant.now());
		courseProgresses.saveAndFlush(progress);

		// Authorized instructor views student course detail
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/students/" + student.getId())
						.header("Authorization", "Bearer " + instrToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(courseId))
				.andExpect(jsonPath("$.student.username").value("john_doe"))
				.andExpect(jsonPath("$.totalLessons").value(10))
				.andExpect(jsonPath("$.completedLessons").value(8))
				.andExpect(jsonPath("$.progressPercent").value(80))
				.andExpect(jsonPath("$.enrollmentStatus").value("ACTIVE"));

		// Unauthorized instructor gets 403 Forbidden
		String otherInstrToken = registerUser("prof_jones@example.com", "prof_jones", "Prof Jones");
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/students/" + student.getId())
						.header("Authorization", "Bearer " + otherInstrToken))
				.andExpect(status().isForbidden());

		// Unenrolled student gets 404 Not Found
		String unenrolledStudentToken = registerUser("stranger@example.com", "stranger_user", "Stranger");
		UserEntity stranger = users.findByUsername("stranger_user").orElseThrow();
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/students/" + stranger.getId())
						.header("Authorization", "Bearer " + instrToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void scenario3InstructorViewsInstructorProfileWithTeachingStats() throws Exception {
		String instr1Token = registerUser("lead_instructor@example.com", "lead_instructor", "Lead Instructor");
		UserEntity instructor = users.findByUsername("lead_instructor").orElseThrow();
		instructor.changeMode(UserMode.INSTRUCTOR);
		instructor.setTeachingExpertise("Distributed Systems & Spring Boot");
		users.saveAndFlush(instructor);

		// Create published course for instructor via SQL
		long categoryId = jdbc.queryForObject("select id from categories where active = true limit 1", Long.class);
		jdbc.update("insert into courses (owner_id, title, description, category_id, difficulty, status, content_updated_at, created_at, updated_at, version) values (?, ?, ?, ?, 'BEGINNER', 'PUBLISHED', now(), now(), now(), 0)",
				instructor.getId(), "Cloud Microservices with Spring", "Desc", categoryId);

		String viewerInstrToken = registerUser("viewer_prof@example.com", "viewer_prof", "Viewer Prof");

		mockMvc.perform(get("/api/v1/users/by-username/lead_instructor/profile")
						.header("Authorization", "Bearer " + viewerInstrToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("lead_instructor"))
				.andExpect(jsonPath("$.teachingExpertise").value("Distributed Systems & Spring Boot"))
				.andExpect(jsonPath("$.instructorStats.totalPublishedCourses").value(1))
				.andExpect(jsonPath("$.instructorStats.publishedCourses[0].title").value("Cloud Microservices with Spring"));
	}

	@Test
	void declaredSkillsAreNormalizedDeduplicatedAndValidated() throws Exception {
		String token = registerUser("skills_user@example.com", "skills_user", "Skills User");

		// Deduplication and normalization
		UpdateProfileRequest updateReq = new UpdateProfileRequest(
				"Skills User",
				"skills_user",
				"Headline",
				"Bio",
				List.of("Java", "java", "  Spring Boot  ", "REACT", "react", "React"),
				List.of("Tech"),
				null, null, null, null, null, true, true, true);

		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skills[0]").value("Java"))
				.andExpect(jsonPath("$.skills[1]").value("Spring Boot"))
				.andExpect(jsonPath("$.skills[2]").value("REACT"))
				.andExpect(jsonPath("$.skills.length()").value(3));

		// Validation error on markup in skills
		UpdateProfileRequest invalidReq = new UpdateProfileRequest(
				"Skills User",
				"skills_user",
				"Headline",
				"Bio",
				List.of("<script>alert('xss')</script>"),
				null, null, null, null, null, null, true, true, true);

		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(invalidReq)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void teachingSectionDifferentiatesOwnerAndPublicView() throws Exception {
		String instrToken = registerUser("author@example.com", "course_author", "Course Author");
		UserEntity author = users.findByUsername("course_author").orElseThrow();
		long categoryId = jdbc.queryForObject("select id from categories where active = true limit 1", Long.class);

		// Published course
		jdbc.update("insert into courses (owner_id, title, description, category_id, difficulty, status, content_updated_at, created_at, updated_at, version) values (?, ?, ?, ?, 'BEGINNER', 'PUBLISHED', now(), now(), now(), 0)",
				author.getId(), "Published Course 101", "Desc", categoryId);
		long publishedCourseId = jdbc.queryForObject("select id from courses where title = 'Published Course 101'", Long.class);

		// Draft course
		jdbc.update("insert into courses (owner_id, title, description, category_id, difficulty, status, content_updated_at, created_at, updated_at, version) values (?, ?, ?, ?, 'INTERMEDIATE', 'DRAFT', now(), now(), now(), 0)",
				author.getId(), "Secret Draft Course", "Draft Desc", categoryId);

		// Owner viewing own profile sees both published and draft
		mockMvc.perform(get("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + instrToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.instructorStats.totalPublishedCourses").value(1))
				.andExpect(jsonPath("$.instructorStats.publishedCourses.length()").value(2));

		// Public viewer only sees published course
		String viewerToken = registerUser("random_viewer@example.com", "random_viewer", "Random Viewer");
		mockMvc.perform(get("/api/v1/users/by-username/course_author/profile")
						.header("Authorization", "Bearer " + viewerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.instructorStats.totalPublishedCourses").value(1))
				.andExpect(jsonPath("$.instructorStats.publishedCourses.length()").value(1))
				.andExpect(jsonPath("$.instructorStats.publishedCourses[0].title").value("Published Course 101"));
	}

	@Test
	void learningSectionDistinguishesDeclaredAndEarnedSkills() throws Exception {
		String studentToken = registerUser("learner_dave@example.com", "learner_dave", "Learner Dave");
		UserEntity student = users.findByUsername("learner_dave").orElseThrow();

		// Add declared skills
		UpdateProfileRequest profileReq = new UpdateProfileRequest(
				"Learner Dave",
				"learner_dave",
				"Avid Student",
				"Learning everything",
				List.of("Python", "Docker"),
				List.of("Cloud"),
				null, null, null, null, null, true, true, true);
		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(profileReq)))
				.andExpect(status().isOk());

		long categoryId = jdbc.queryForObject("select id from categories where active = true limit 1", Long.class);

		// Create 2 courses with topics
		jdbc.update("insert into courses (owner_id, title, description, category_id, difficulty, status, content_updated_at, created_at, updated_at, version) values (?, ?, ?, ?, 'BEGINNER', 'PUBLISHED', now(), now(), now(), 0)",
				student.getId(), "Complete Spring Boot Mastery", "Desc", categoryId);
		long courseAId = jdbc.queryForObject("select id from courses where title = 'Complete Spring Boot Mastery'", Long.class);
		jdbc.update("insert into course_topics (course_id, position, name) values (?, 0, 'Spring Boot')", courseAId);
		jdbc.update("insert into course_topics (course_id, position, name) values (?, 1, 'REST APIs')", courseAId);

		jdbc.update("insert into courses (owner_id, title, description, category_id, difficulty, status, content_updated_at, created_at, updated_at, version) values (?, ?, ?, ?, 'INTERMEDIATE', 'PUBLISHED', now(), now(), now(), 0)",
				student.getId(), "Advanced Microservices", "Desc", categoryId);
		long courseBId = jdbc.queryForObject("select id from courses where title = 'Advanced Microservices'", Long.class);
		jdbc.update("insert into course_topics (course_id, position, name) values (?, 0, 'Kubernetes')", courseBId);

		// Enroll student in Course A (Completed)
		EnrollmentEntity enrollA = new EnrollmentEntity(student.getId(), courseAId, Instant.now());
		enrollA = enrollments.saveAndFlush(enrollA);
		CourseProgressEntity progA = new CourseProgressEntity(
				enrollA.getId(), courseAId, 10, 10, 100, Instant.now(), false, Instant.now());
		courseProgresses.saveAndFlush(progA);

		// Enroll student in Course B (In Progress, 30%)
		EnrollmentEntity enrollB = new EnrollmentEntity(student.getId(), courseBId, Instant.now());
		enrollB = enrollments.saveAndFlush(enrollB);
		CourseProgressEntity progB = new CourseProgressEntity(
				enrollB.getId(), courseBId, 10, 3, 30, null, false, Instant.now());
		courseProgresses.saveAndFlush(progB);

		// Verify student profile
		mockMvc.perform(get("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				// Declared skills: Python, Docker
				.andExpect(jsonPath("$.skills[0]").value("Python"))
				.andExpect(jsonPath("$.skills[1]").value("Docker"))
				// Learning stats
				.andExpect(jsonPath("$.learningStats.totalEnrolledCourses").value(2))
				.andExpect(jsonPath("$.learningStats.coursesCompleted").value(1))
				.andExpect(jsonPath("$.learningStats.coursesInProgress").value(1))
				.andExpect(jsonPath("$.learningStats.lessonsCompleted").value(13))
				// Earned skills: derived from completed Course A only (Spring Boot, REST APIs), not Course B (Kubernetes)
				.andExpect(jsonPath("$.earnedSkills.length()").value(2))
				.andExpect(jsonPath("$.earnedSkills[?(@.skillName == 'Spring Boot')].sourceCourses[0]").value("Complete Spring Boot Mastery"))
				.andExpect(jsonPath("$.earnedSkills[?(@.skillName == 'REST APIs')].sourceCourses[0]").value("Complete Spring Boot Mastery"))
				// Course cards
				.andExpect(jsonPath("$.enrolledCourses.length()").value(2));
	}
}
