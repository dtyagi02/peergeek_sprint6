package com.peergeek.doubtsession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.course.CourseEntity;
import com.peergeek.course.CourseRepository;
import com.peergeek.course.CourseStatus;
import com.peergeek.doubtsession.dto.CreateDoubtSessionRequest;
import com.peergeek.doubtsession.dto.DoubtSessionDto;
import com.peergeek.doubtsession.dto.UpdateDoubtSessionSettingsRequest;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import com.peergeek.wallet.WalletEntity;
import com.peergeek.wallet.WalletRepository;
import com.peergeek.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoubtSessionIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository users;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private WalletService walletService;

	@Autowired
	private CourseDoubtSessionSettingsRepository settingsRepository;

	@Autowired
	private DoubtSessionRepository sessionRepository;

	@Autowired
	private DoubtSessionService doubtSessionService;

	@Autowired
	private DoubtSessionTimeoutScheduler timeoutScheduler;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	private UserEntity instructor;
	private UserEntity student;
	private CourseEntity course;

	private UserEntity createUser(String email, String username, UserRole role, UserMode mode) {
		UserEntity user = new UserEntity(email, "hashedPassword", username, username, true);
		user.setRole(role);
		user.changeMode(mode);
		return users.save(user);
	}

	@BeforeEach
	void setUp() {
		jdbc.update("delete from wallet_transactions");
		jdbc.update("delete from doubt_sessions");
		jdbc.update("delete from course_doubt_session_settings");
		jdbc.update("delete from enrollments");
		jdbc.update("delete from courses");
		jdbc.update("delete from wallets");
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from users");

		String uid = UUID.randomUUID().toString().substring(0, 8);
		instructor = createUser("inst_" + uid + "@example.com", "inst_" + uid, UserRole.USER, UserMode.INSTRUCTOR);
		student = createUser("stud_" + uid + "@example.com", "stud_" + uid, UserRole.USER, UserMode.STUDENT);

		course = new CourseEntity(instructor, "Java Mastery 101", "Learn Java from scratch", null, CourseDifficulty.BEGINNER, Instant.now());
		course = courseRepository.save(course);
		jdbc.update("update courses set status = 'PUBLISHED' where id = ?", course.getId());
	}

	@Test
	@DisplayName("Public availability returns defaults and updates correctly")
	void courseAvailabilityReturnsSettingsWhenConfigured() throws Exception {
		// Initially not configured, should return default disabled/unavailable
		mockMvc.perform(get("/api/v1/public/courses/" + course.getId() + "/doubt-session-availability"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(course.getId()))
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.available").value(false));

		// Instructor configures settings: enabled = true, available = true, hourlyRate = 720
		String instructorToken = jwtService.issue(instructor);
		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 720);

		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.hourlyRatePgc").value(720))
				.andExpect(jsonPath("$.available").value(true));

		// Public endpoint reflects updated settings
		mockMvc.perform(get("/api/v1/public/courses/" + course.getId() + "/doubt-session-availability"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.hourlyRatePgc").value(720));
	}

	@Test
	@DisplayName("Non-owner instructor cannot update course settings")
	void nonOwnerCannotUpdateSettings() throws Exception {
		UserEntity otherInstructor = createUser("other_inst@example.com", "other_inst", UserRole.USER, UserMode.INSTRUCTOR);
		String otherToken = jwtService.issue(otherInstructor);

		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 600);
		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Student cannot request doubt session if not enrolled in course")
	void studentCannotRequestSessionIfNotEnrolled() throws Exception {
		// Enable course doubt sessions
		CourseDoubtSessionSettingsEntity settings = new CourseDoubtSessionSettingsEntity(course, instructor, true, true, 600);
		settingsRepository.save(settings);

		// Provision student wallet with funds
		WalletEntity studentWallet = walletService.getOrCreateWallet(student);
		studentWallet.setBalance(new BigDecimal("1000.00"));
		walletRepository.save(studentWallet);

		String studentToken = jwtService.issue(student);
		CreateDoubtSessionRequest req = new CreateDoubtSessionRequest(course.getId(), 15);

		mockMvc.perform(post("/api/v1/doubt-sessions/request")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("You must be actively enrolled in this course to request doubt clearing."));
	}

	@Test
	@DisplayName("Student cannot request doubt session with insufficient GeekCoins balance")
	void studentCannotRequestSessionWithInsufficientFunds() throws Exception {
		// Enable course doubt sessions
		CourseDoubtSessionSettingsEntity settings = new CourseDoubtSessionSettingsEntity(course, instructor, true, true, 600);
		settingsRepository.save(settings);

		// Enroll student
		enrollmentRepository.save(new EnrollmentEntity(student.getId(), course.getId(), Instant.now()));

		// Student wallet has 50 PGC, but 15-min session costs 150 PGC
		WalletEntity studentWallet = walletService.getOrCreateWallet(student);
		studentWallet.setBalance(new BigDecimal("50.00"));
		walletRepository.save(studentWallet);

		String studentToken = jwtService.issue(student);
		CreateDoubtSessionRequest req = new CreateDoubtSessionRequest(course.getId(), 15);

		mockMvc.perform(post("/api/v1/doubt-sessions/request")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Insufficient GeekCoins balance. Available: 50.00 PGC, required: 150.00 PGC"));
	}

	@Test
	@DisplayName("Successful session request reserves funds and instructor can accept and end call with ceiling billing")
	void fullSessionLifecycleWithBillingAndRefund() throws Exception {
		// Enable course doubt sessions at 600 PGC/h
		CourseDoubtSessionSettingsEntity settings = new CourseDoubtSessionSettingsEntity(course, instructor, true, true, 600);
		settingsRepository.save(settings);

		// Enroll student
		enrollmentRepository.save(new EnrollmentEntity(student.getId(), course.getId(), Instant.now()));

		// Student has 500 PGC
		WalletEntity studentWallet = walletService.getOrCreateWallet(student);
		studentWallet.setBalance(new BigDecimal("500.00"));
		walletRepository.save(studentWallet);

		WalletEntity instWallet = walletService.getOrCreateWallet(instructor);
		instWallet.setBalance(BigDecimal.ZERO);
		walletRepository.save(instWallet);

		String studentToken = jwtService.issue(student);
		String instructorToken = jwtService.issue(instructor);

		// 1. Student requests 15-min session (maxCost = (15 / 60) * 600 = 150 PGC)
		CreateDoubtSessionRequest req = new CreateDoubtSessionRequest(course.getId(), 15);
		String responseContent = mockMvc.perform(post("/api/v1/doubt-sessions/request")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andExpect(jsonPath("$.reservedAmount").value(150.00))
				.andExpect(jsonPath("$.maxDurationMinutes").value(15))
				.andReturn().getResponse().getContentAsString();

		Long sessionId = objectMapper.readTree(responseContent).get("id").asLong();

		// Verify funds are reserved in student's wallet
		studentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
		assertThat(studentWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
		assertThat(studentWallet.getReservedBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
		assertThat(studentWallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("350.00"));

		// 2. Instructor accepts session
		mockMvc.perform(post("/api/v1/instructor/doubt-sessions/" + sessionId + "/accept")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		// 3. Both student and instructor can retrieve LiveKit join tokens
		mockMvc.perform(get("/api/v1/doubt-sessions/" + sessionId + "/token")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.roomName").isNotEmpty());

		mockMvc.perform(get("/api/v1/doubt-sessions/" + sessionId + "/token")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());

		// 4. Simulate room connected: billable time is 320 seconds (5 mins 20 seconds)
		// 5 mins 20s rounded UP to 6 whole minutes: (6 * 600) / 60 = 60 PGC.
		// Student charged 60 PGC, remaining 90 PGC released.
		// Instructor receives 60 PGC.
		DoubtSessionEntity session = sessionRepository.findById(sessionId).orElseThrow();
		session.setStatus(DoubtSessionStatus.IN_PROGRESS);
		session.setStartedAt(Instant.now().minusSeconds(320));
		session.setBillableSeconds(320);
		sessionRepository.save(session);

		// End session
		mockMvc.perform(post("/api/v1/doubt-sessions/" + sessionId + "/end")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.billableSeconds").value(320))
				.andExpect(jsonPath("$.finalCharge").value(60.00))
				.andExpect(jsonPath("$.refundedAmount").value(90.00));

		// Verify student wallet: 500 - 60 = 440 balance, 0 reserved
		studentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
		assertThat(studentWallet.getBalance()).isEqualByComparingTo(new BigDecimal("440.00"));
		assertThat(studentWallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(studentWallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("440.00"));

		// Verify instructor wallet: 0 + 60 = 60 balance
		instWallet = walletRepository.findByUserId(instructor.getId()).orElseThrow();
		assertThat(instWallet.getBalance()).isEqualByComparingTo(new BigDecimal("60.00"));
	}

	@Test
	@DisplayName("Rejecting a session releases reserved funds completely")
	void rejectingSessionReleasesReservedFunds() throws Exception {
		CourseDoubtSessionSettingsEntity settings = new CourseDoubtSessionSettingsEntity(course, instructor, true, true, 600);
		settingsRepository.save(settings);

		enrollmentRepository.save(new EnrollmentEntity(student.getId(), course.getId(), Instant.now()));

		WalletEntity studentWallet = walletService.getOrCreateWallet(student);
		studentWallet.setBalance(new BigDecimal("200.00"));
		walletRepository.save(studentWallet);

		String studentToken = jwtService.issue(student);
		String instructorToken = jwtService.issue(instructor);

		CreateDoubtSessionRequest req = new CreateDoubtSessionRequest(course.getId(), 10);
		String responseContent = mockMvc.perform(post("/api/v1/doubt-sessions/request")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Long sessionId = objectMapper.readTree(responseContent).get("id").asLong();

		// Instructor rejects session
		mockMvc.perform(post("/api/v1/instructor/doubt-sessions/" + sessionId + "/reject")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		// Funds fully released
		studentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
		assertThat(studentWallet.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
		assertThat(studentWallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("Expired session is timed out and funds are released by scheduler")
	void expiredSessionReleasesFunds() {
		CourseDoubtSessionSettingsEntity settings = new CourseDoubtSessionSettingsEntity(course, instructor, true, true, 600);
		settingsRepository.save(settings);

		enrollmentRepository.save(new EnrollmentEntity(student.getId(), course.getId(), Instant.now()));

		WalletEntity studentWallet = walletService.getOrCreateWallet(student);
		studentWallet.setBalance(new BigDecimal("200.00"));
		walletRepository.save(studentWallet);

		DoubtSessionDto sessionDto = doubtSessionService.requestSession(student.getId(), new CreateDoubtSessionRequest(course.getId(), 10));
		DoubtSessionEntity session = sessionRepository.findById(sessionDto.id()).orElseThrow();
		// Force requestedAt and expiresAt to past
		session.setRequestedAt(Instant.now().minusSeconds(70));
		session.setExpiresAt(Instant.now().minusSeconds(10));
		sessionRepository.save(session);

		// Trigger scheduler
		timeoutScheduler.processSessionTimeouts();

		DoubtSessionEntity updated = sessionRepository.findById(session.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(DoubtSessionStatus.EXPIRED);

		studentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
		assertThat(studentWallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("Instructor can retrieve default settings and save new settings")
	void instructorCanRetrieveAndSaveSettings() throws Exception {
		String instructorToken = jwtService.issue(instructor);

		// 1. Initial GET returns defaults (enabled=false, available=false, hourlyRatePgc=600)
		mockMvc.perform(get("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(course.getId()))
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.available").value(false))
				.andExpect(jsonPath("$.hourlyRatePgc").value(600));

		// 2. PUT updates settings
		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 800);
		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(course.getId()))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.hourlyRatePgc").value(800));

		// 3. Subsequent GET returns the persisted settings
		mockMvc.perform(get("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(course.getId()))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.hourlyRatePgc").value(800));
	}

	@Test
	@DisplayName("Settings endpoints return 404 when course does not exist")
	void settingsEndpointsReturn404WhenCourseDoesNotExist() throws Exception {
		String instructorToken = jwtService.issue(instructor);
		long nonExistentCourseId = 999999L;

		// GET on missing course returns 404 with course not found message
		mockMvc.perform(get("/api/v1/instructor/courses/" + nonExistentCourseId + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Course not found: " + nonExistentCourseId));

		// PUT on missing course returns 404 with course not found message
		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 600);
		mockMvc.perform(put("/api/v1/instructor/courses/" + nonExistentCourseId + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Course not found: " + nonExistentCourseId));
	}

	@Test
	@DisplayName("Settings endpoints return 403 when user is not the course owner")
	void settingsEndpointsReturn403WhenNotCourseOwner() throws Exception {
		UserEntity otherInstructor = createUser("another_inst@example.com", "another_inst", UserRole.USER, UserMode.INSTRUCTOR);
		String otherToken = jwtService.issue(otherInstructor);

		// GET by non-owner returns 403
		mockMvc.perform(get("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("Only the instructor who owns this course can manage its doubt clearing settings."));

		// PUT by non-owner returns 403
		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 600);
		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("Only the instructor who owns this course can manage its doubt clearing settings."));
	}

	@Test
	@DisplayName("Updating settings with invalid hourly rate (< 1) returns 400 Bad Request")
	void updatingSettingsWithInvalidHourlyRateReturnsBadRequest() throws Exception {
		String instructorToken = jwtService.issue(instructor);

		// Zero rate
		UpdateDoubtSessionSettingsRequest zeroRateReq = new UpdateDoubtSessionSettingsRequest(true, true, 0);
		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(zeroRateReq)))
				.andExpect(status().isBadRequest());

		// Negative rate
		UpdateDoubtSessionSettingsRequest negativeRateReq = new UpdateDoubtSessionSettingsRequest(true, true, -100);
		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(negativeRateReq)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Changing instructor rate affects future sessions only; existing sessions retain snapshot rate")
	void changingRateAffectsNewSessionsOnlyAndPreservesExistingSessionPricing() throws Exception {
		// 1. Initial settings with 600 PGC/h
		CourseDoubtSessionSettingsEntity settings = new CourseDoubtSessionSettingsEntity(course, instructor, true, true, 600);
		settingsRepository.save(settings);

		enrollmentRepository.save(new EnrollmentEntity(student.getId(), course.getId(), Instant.now()));

		WalletEntity studentWallet = walletService.getOrCreateWallet(student);
		studentWallet.setBalance(new BigDecimal("1000.00"));
		walletRepository.save(studentWallet);

		String instructorToken = jwtService.issue(instructor);
		String studentToken = jwtService.issue(student);

		// 2. Student requests session 1 (15m @ 600 PGC/h -> maxCost = 150 PGC)
		CreateDoubtSessionRequest req1 = new CreateDoubtSessionRequest(course.getId(), 15);
		String res1 = mockMvc.perform(post("/api/v1/doubt-sessions/request")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hourlyRatePgc").value(600))
				.andExpect(jsonPath("$.reservedAmount").value(150.0))
				.andReturn().getResponse().getContentAsString();

		Long session1Id = objectMapper.readTree(res1).get("id").asLong();

		// 3. Instructor changes rate to 1200 PGC/h
		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 1200);
		mockMvc.perform(put("/api/v1/instructor/courses/" + course.getId() + "/doubt-session-settings")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hourlyRatePgc").value(1200));

		// 4. Session 1 is finalized (instructor rejects or ends it)
		mockMvc.perform(post("/api/v1/instructor/doubt-sessions/" + session1Id + "/reject")
						.header("Authorization", "Bearer " + instructorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.hourlyRatePgc").value(600)); // still 600!

		// Existing session 1 in DB has hourlyRate = 600
		DoubtSessionEntity s1Entity = sessionRepository.findById(session1Id).orElseThrow();
		assertThat(s1Entity.getHourlyRatePgc()).isEqualTo(600);

		// 5. Student requests session 2 (15m @ 1200 PGC/h -> reservedAmount = 300 PGC)
		CreateDoubtSessionRequest req2 = new CreateDoubtSessionRequest(course.getId(), 15);
		mockMvc.perform(post("/api/v1/doubt-sessions/request")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hourlyRatePgc").value(1200))
				.andExpect(jsonPath("$.reservedAmount").value(300.0));
	}
}
