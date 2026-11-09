package com.peergeek.admin;

import java.util.Map;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.course.CourseStatus;
import com.peergeek.enrollment.LearningTestSupport;
import com.peergeek.report.ReportCategory;
import com.peergeek.report.ReportEntity;
import com.peergeek.report.ReportRepository;
import com.peergeek.report.ReportStatus;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDashboardIntegrationTest extends LearningTestSupport {

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private AuditService auditService;

	protected UserEntity createAdmin(String email, String displayName) {
		UserEntity admin = new UserEntity(email, "hashed-pass-1234", displayName);
		admin.setRole(UserRole.ADMIN);
		admin.changeMode(UserMode.INSTRUCTOR);
		return userRepository.saveAndFlush(admin);
	}

	@Test
	@DisplayName("Admin dashboard summary returns live counts")
	void summaryReturnsRealCounts() throws Exception {
		UserEntity admin = createAdmin("admin.dash@test.local", "Super Admin");
		String adminToken = tokenFor(admin);

		UserEntity instructor = createInstructor("prof.dash@test.local", "Prof Dash");
		instructor.changeMode(UserMode.INSTRUCTOR);
		userRepository.saveAndFlush(instructor);
		String instructorToken = tokenFor(instructor);

		UserEntity learner = createUser("student.dash@test.local", "Student Dash");
		learner.changeMode(UserMode.STUDENT);
		userRepository.saveAndFlush(learner);

		CourseCreationResult c1 = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, c1.courseId());

		CourseCreationResult c2 = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		// c2 remains DRAFT

		ReportEntity report = new ReportEntity(
				c1.courseId(),
				learner.getId(),
				ReportCategory.BROKEN_RESOURCE,
				"Broken download link");
		reportRepository.saveAndFlush(report);

		mockMvc.perform(get("/api/v1/admin/dashboard/summary")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalUsers").value(3))
				.andExpect(jsonPath("$.totalStudents").value(1))
				.andExpect(jsonPath("$.totalInstructors").value(2))
				.andExpect(jsonPath("$.totalCourses").value(2))
				.andExpect(jsonPath("$.publishedCourses").value(1))
				.andExpect(jsonPath("$.openReportsCount").value(1));
	}

	@Test
	@DisplayName("Admin can view paginated activities sorted newest first")
	void activitiesFeedIsPaginatedAndSorted() throws Exception {
		UserEntity admin = createAdmin("admin.act@test.local", "Admin Act");
		String adminToken = tokenFor(admin);

		auditService.record(new AuditEvent(
				admin.getId(), "USER_REGISTERED", "USER", admin.getId().toString(),
				Map.of("role", "ADMIN", "email", admin.getEmail())));
		auditService.record(new AuditEvent(
				admin.getId(), "COURSE_CREATED", "COURSE", "101",
				Map.of("title", "Distributed Systems 101")));

		mockMvc.perform(get("/api/v1/admin/dashboard/activities?page=0&size=10")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").isNumber())
				.andExpect(jsonPath("$.content[0].action").value("COURSE_CREATED"))
				.andExpect(jsonPath("$.content[0].title").value("Course Created"))
				.andExpect(jsonPath("$.content[1].action").value("USER_REGISTERED"))
				.andExpect(jsonPath("$.content[1].title").value("New User Registration"));
	}

	@Test
	@DisplayName("Admin can filter and resolve reported course issues")
	void adminCanFilterAndResolveReports() throws Exception {
		UserEntity admin = createAdmin("admin.rep@test.local", "Admin Reports");
		String adminToken = tokenFor(admin);

		UserEntity instructor = createInstructor("prof.target@test.local", "Prof Target");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		UserEntity learner = createUser("learner.target@test.local", "Learner Target");

		ReportEntity report = new ReportEntity(
				course.courseId(),
				learner.getId(),
				ReportCategory.INCORRECT_OR_MISLEADING_INFORMATION,
				"Slide 3 contains an outdated formula.");
		report = reportRepository.saveAndFlush(report);

		// List reports
		mockMvc.perform(get("/api/v1/admin/reports?status=PENDING")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(report.getId()))
				.andExpect(jsonPath("$.content[0].courseTitle").value("Computer Systems Architecture"))
				.andExpect(jsonPath("$.content[0].reporterName").value("Learner Target"))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"));

		// Resolve report
		mockMvc.perform(patch("/api/v1/admin/reports/{reportId}", report.getId())
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"RESOLVED\", \"resolutionNotes\": \"Formula updated in curriculum v2.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.resolutionNotes").value("Formula updated in curriculum v2."))
				.andExpect(jsonPath("$.resolverName").value("Admin Reports"));

		ReportEntity updated = reportRepository.findById(report.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		assertThat(updated.getResolvedBy()).isEqualTo(admin.getId());
	}

	@Test
	@DisplayName("Admin can view and filter registered users")
	void adminCanViewAndFilterUsers() throws Exception {
		UserEntity admin = createAdmin("admin.users@test.local", "Admin Users");
		String adminToken = tokenFor(admin);
		createUser("regular.user@test.local", "Regular User");

		// Filter role=USER
		mockMvc.perform(get("/api/v1/admin/users?role=USER")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].email").value("regular.user@test.local"))
				.andExpect(jsonPath("$.content[0].role").value("USER"));

		// Filter role=ADMIN
		mockMvc.perform(get("/api/v1/admin/users?role=ADMIN")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].email").value("admin.users@test.local"))
				.andExpect(jsonPath("$.content[0].role").value("ADMIN"));
	}

	@Test
	@DisplayName("Admin can view and search courses with enrollment counts")
	void adminCanViewAndSearchCourses() throws Exception {
		UserEntity admin = createAdmin("admin.courses@test.local", "Admin Courses");
		String adminToken = tokenFor(admin);

		UserEntity instructor = createInstructor("prof.courses@test.local", "Prof Courses");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student = createUser("student.courses@test.local", "Student Courses");
		enrollmentService.enroll(course.courseId(), student.getId());

		mockMvc.perform(get("/api/v1/admin/courses?status=PUBLISHED&query=Computer")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(course.courseId()))
				.andExpect(jsonPath("$.content[0].title").value("Computer Systems Architecture"))
				.andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
				.andExpect(jsonPath("$.content[0].enrollmentCount").value(1));
	}

	@Test
	@DisplayName("Regular user receives 403 Forbidden for admin endpoints")
	void nonAdminReceivesForbidden() throws Exception {
		UserEntity learner = createUser("learner.norm@test.local", "Normal Learner");
		String learnerToken = tokenFor(learner);

		mockMvc.perform(get("/api/v1/admin/dashboard/summary")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/dashboard/activities")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/reports")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/users")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/courses")
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Unauthenticated request receives 401 Unauthorized for admin endpoints")
	void unauthenticatedReceivesUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Student submits course issue report, which is persisted and visible to admin with correct details")
	void studentReportAppearsInAdminDashboard() throws Exception {
		UserEntity admin = createAdmin("admin.flow@test.local", "Admin Flow");
		String adminToken = tokenFor(admin);

		UserEntity instructor = createInstructor("prof.flow@test.local", "Prof Flow");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		UserEntity student = createUser("student.reporter@test.local", "Student Reporter");
		String studentToken = tokenFor(student);

		long initialReportCount = reportRepository.count();

		// 1. Student submits a course issue report
		String reportPayload = """
				{
					"category": "BROKEN_RESOURCE",
					"details": "Video player throws playback error on lesson 1"
				}
				""";

		mockMvc.perform(post("/api/v1/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reportPayload))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.courseId").value(course.courseId()))
				.andExpect(jsonPath("$.reporterUserId").value(student.getId()))
				.andExpect(jsonPath("$.reporterName").value("Student Reporter"))
				.andExpect(jsonPath("$.category").value("BROKEN_RESOURCE"))
				.andExpect(jsonPath("$.details").value("Video player throws playback error on lesson 1"))
				.andExpect(jsonPath("$.status").value("PENDING"));

		// 2. Verify report is persisted in database
		assertThat(reportRepository.count()).isEqualTo(initialReportCount + 1);

		// 3. Admin retrieves reports without status filter (ALL)
		mockMvc.perform(get("/api/v1/admin/reports")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].courseId").value(course.courseId()))
				.andExpect(jsonPath("$.content[0].courseTitle").value("Computer Systems Architecture"))
				.andExpect(jsonPath("$.content[0].reporterUserId").value(student.getId()))
				.andExpect(jsonPath("$.content[0].reporterName").value("Student Reporter"))
				.andExpect(jsonPath("$.content[0].reporterEmail").value("student.reporter@test.local"))
				.andExpect(jsonPath("$.content[0].category").value("BROKEN_RESOURCE"))
				.andExpect(jsonPath("$.content[0].details").value("Video player throws playback error on lesson 1"))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());

		// 4. Admin retrieves reports with status=PENDING filter
		mockMvc.perform(get("/api/v1/admin/reports?status=PENDING")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].status").value("PENDING"));

		// 5. Admin retrieves reports with search query matching report details
		mockMvc.perform(get("/api/v1/admin/reports?query=playback")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].details").value("Video player throws playback error on lesson 1"));

		// 6. Admin retrieves reports with status=RESOLVED filter (should NOT include pending report)
		mockMvc.perform(get("/api/v1/admin/reports?status=RESOLVED")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	@DisplayName("Invalid report submissions return validation errors without persisting")
	void invalidReportSubmissionsFail() throws Exception {
		UserEntity instructor = createInstructor("prof.invalid@test.local", "Prof Invalid");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		UserEntity student = createUser("student.invalid@test.local", "Student Invalid");
		String studentToken = tokenFor(student);

		long countBefore = reportRepository.count();

		// Empty details
		mockMvc.perform(post("/api/v1/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\": \"BROKEN_RESOURCE\", \"details\": \"\"}"))
				.andExpect(status().isBadRequest());

		// Non-existent course
		mockMvc.perform(post("/api/v1/courses/{courseId}/reports", 999999L)
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\": \"BROKEN_RESOURCE\", \"details\": \"Valid issue details\"}"))
				.andExpect(status().isNotFound());

		// Database records count should remain unchanged
		assertThat(reportRepository.count()).isEqualTo(countBefore);
	}

	@Test
	@DisplayName("Admin can block eligible user and revoked user cannot access protected endpoints; admin can unblock")
	void adminCanBlockAndUnblockUser() throws Exception {
		UserEntity admin = createAdmin("admin.mod@test.local", "Admin Mod");
		String adminToken = tokenFor(admin);

		UserEntity student = createUser("student.blocked@test.local", "Student ToBlock");
		String studentToken = tokenFor(student);

		// Verify student initially can access protected profile endpoint
		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		// Admin blocks student
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", student.getId())
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\": false, \"reason\": \"Violation of platform terms.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(student.getId()))
				.andExpect(jsonPath("$.enabled").value(false));

		UserEntity reloadedStudent = userRepository.findById(student.getId()).orElseThrow();
		assertThat(reloadedStudent.isEnabled()).isFalse();

		// Blocked user token is immediately rejected on protected requests
		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isUnauthorized());

		// Admin unblocks student
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", student.getId())
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\": true, \"reason\": \"Account reinstated after appeal.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true));

		reloadedStudent = userRepository.findById(student.getId()).orElseThrow();
		assertThat(reloadedStudent.isEnabled()).isTrue();

		// Re-authenticated user can access protected endpoints again
		String newStudentToken = tokenFor(reloadedStudent);
		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", bearer(newStudentToken)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Admin cannot block their own account or other admin accounts")
	void adminCannotBlockSelfOrOtherAdmin() throws Exception {
		UserEntity admin1 = createAdmin("admin1.block@test.local", "Admin One");
		String admin1Token = tokenFor(admin1);

		UserEntity admin2 = createAdmin("admin2.block@test.local", "Admin Two");

		// Self-block attempt returns 400
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", admin1.getId())
						.header("Authorization", bearer(admin1Token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\": false, \"reason\": \"Self disable\"}"))
				.andExpect(status().isBadRequest());

		// Other admin block attempt returns 400
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", admin2.getId())
						.header("Authorization", bearer(admin1Token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\": false, \"reason\": \"Block admin\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Non-admin user cannot block users")
	void nonAdminCannotBlockUser() throws Exception {
		UserEntity learner = createUser("regular.student.noblock@test.local", "Regular Student");
		String learnerToken = tokenFor(learner);

		UserEntity target = createUser("victim.noblock@test.local", "Victim User");

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", target.getId())
						.header("Authorization", bearer(learnerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\": false}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Admin can unpublish and restore course; unpublishing hides course from learners and preserves reports")
	void adminCanModerateCourseStatusAndPreserveReports() throws Exception {
		UserEntity admin = createAdmin("admin.course.mod@test.local", "Admin CourseMod");
		String adminToken = tokenFor(admin);

		UserEntity instructor = createInstructor("prof.mod@test.local", "Prof Mod");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner = createUser("learner.course.mod@test.local", "Learner Mod");
		String learnerToken = tokenFor(learner);

		// Create report on course
		ReportEntity report = new ReportEntity(
				course.courseId(),
				learner.getId(),
				ReportCategory.INCORRECT_OR_MISLEADING_INFORMATION,
				"Misleading course description.");
		report = reportRepository.saveAndFlush(report);

		// Admin unpublishes course
		mockMvc.perform(patch("/api/v1/admin/courses/{courseId}/status", course.courseId())
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"UNPUBLISHED\", \"reason\": \"Content under investigation.\", \"reportId\": " + report.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(course.courseId()))
				.andExpect(jsonPath("$.status").value("UNPUBLISHED"));

		// Learner cannot access unpublished course via public discovery endpoint
		mockMvc.perform(get("/api/v1/public/courses/{courseId}", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNotFound());

		// Verify report remains PENDING (moderating course does not auto-resolve report)
		ReportEntity reportAfterMod = reportRepository.findById(report.getId()).orElseThrow();
		assertThat(reportAfterMod.getStatus()).isEqualTo(ReportStatus.PENDING);

		// Admin restores/republishes course
		mockMvc.perform(patch("/api/v1/admin/courses/{courseId}/status", course.courseId())
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"PUBLISHED\", \"reason\": \"Course cleared after review.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));

		// Learner can access republished course again
		mockMvc.perform(get("/api/v1/public/courses/{courseId}", course.courseId())
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Non-admin receives 403 when attempting to moderate course")
	void nonAdminCannotModerateCourse() throws Exception {
		UserEntity instructor = createInstructor("prof.regular.nomod@test.local", "Prof Regular");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		UserEntity student = createUser("learner.trymod@test.local", "Student TryMod");
		String studentToken = tokenFor(student);

		mockMvc.perform(patch("/api/v1/admin/courses/{courseId}/status", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"UNPUBLISHED\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Admin can filter users by mode (STUDENT vs INSTRUCTOR) and enabled status")
	void filterUsersByModeAndStatus() throws Exception {
		UserEntity admin = createAdmin("admin.filter@test.local", "Admin Filter");
		String adminToken = tokenFor(admin);

		UserEntity student1 = createUser("s1.filter@test.local", "Student 1");
		student1.changeMode(UserMode.STUDENT);
		userRepository.saveAndFlush(student1);

		UserEntity teacher1 = createUser("t1.filter@test.local", "Teacher 1");
		teacher1.changeMode(UserMode.INSTRUCTOR);
		userRepository.saveAndFlush(teacher1);

		// Filter mode=STUDENT
		mockMvc.perform(get("/api/v1/admin/users?mode=STUDENT")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.email == 's1.filter@test.local')]").exists())
				.andExpect(jsonPath("$.content[?(@.email == 't1.filter@test.local')]").doesNotExist());

		// Filter mode=INSTRUCTOR
		mockMvc.perform(get("/api/v1/admin/users?mode=INSTRUCTOR")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.email == 't1.filter@test.local')]").exists())
				.andExpect(jsonPath("$.content[?(@.email == 's1.filter@test.local')]").doesNotExist());
	}

	@Test
	@DisplayName("Admin can filter reports with status=OPEN which returns PENDING and IN_REVIEW reports")
	void filterReportsByOpenStatus() throws Exception {
		UserEntity admin = createAdmin("admin.openrep@test.local", "Admin OpenRep");
		String adminToken = tokenFor(admin);

		UserEntity instructor = createInstructor("prof.openrep@test.local", "Prof OpenRep");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);

		UserEntity learner = createUser("learner.openrep@test.local", "Learner OpenRep");

		ReportEntity repPending = new ReportEntity(course.courseId(), learner.getId(), ReportCategory.BROKEN_RESOURCE, "Pending issue");
		repPending = reportRepository.saveAndFlush(repPending);

		ReportEntity repInReview = new ReportEntity(course.courseId(), learner.getId(), ReportCategory.INCOMPLETE_CONTENT, "In review issue");
		repInReview.setStatus(ReportStatus.IN_REVIEW);
		repInReview = reportRepository.saveAndFlush(repInReview);

		ReportEntity repResolved = new ReportEntity(course.courseId(), learner.getId(), ReportCategory.OTHER, "Resolved issue");
		repResolved.setStatus(ReportStatus.RESOLVED);
		repResolved = reportRepository.saveAndFlush(repResolved);

		mockMvc.perform(get("/api/v1/admin/reports?status=OPEN")
						.header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id == " + repPending.getId() + ")]").exists())
				.andExpect(jsonPath("$.content[?(@.id == " + repInReview.getId() + ")]").exists())
				.andExpect(jsonPath("$.content[?(@.id == " + repResolved.getId() + ")]").doesNotExist());
	}
}
