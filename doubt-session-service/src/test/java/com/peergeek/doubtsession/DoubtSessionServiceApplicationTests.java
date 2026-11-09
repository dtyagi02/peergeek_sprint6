package com.peergeek.doubtsession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import com.peergeek.doubtsession.client.CourseLearningClient;
import com.peergeek.doubtsession.client.WalletServiceClient;
import com.peergeek.doubtsession.dto.*;
import com.peergeek.doubtsession.entity.*;
import com.peergeek.doubtsession.livekit.LiveKitService;
import com.peergeek.doubtsession.repository.*;
import com.peergeek.doubtsession.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {DoubtSessionServiceApplication.class, DoubtSessionServiceApplicationTests.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class DoubtSessionServiceApplicationTests {

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		public WalletServiceClient mockWalletServiceClient() {
			return new WalletServiceClient(new org.springframework.boot.web.client.RestTemplateBuilder(), "http://dummy", "secret") {
				@Override
				public void reserveFunds(Long userId, BigDecimal amount, String referenceType, String referenceId, String description) {}
				@Override
				public void releaseReservedFunds(Long userId, BigDecimal amount, String referenceType, String referenceId, String description) {}
				@Override
				public void settleSessionPayment(Long sId, Long iId, BigDecimal res, BigDecimal chg, String refType, String refId, String desc) {}
			};
		}

		@Bean
		@Primary
		public CourseLearningClient mockCourseLearningClient() {
			return new CourseLearningClient(new org.springframework.boot.web.client.RestTemplateBuilder(), "http://dummy", "secret") {
				@Override
				public boolean isStudentEnrolled(Long courseId, Long studentId) {
					return true;
				}
			};
		}
	}

	@Autowired
	private DoubtSessionRepository sessionRepository;

	@Autowired
	private CourseDoubtSessionSettingsRepository settingsRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private DoubtSessionService doubtSessionService;

	@Autowired
	private LiveKitService liveKitService;

	@Test
	void contextLoads() {
		assertNotNull(doubtSessionService);
		assertNotNull(liveKitService);
		assertNotNull(sessionRepository);
		assertNotNull(settingsRepository);
	}

	@Test
	void testCourseSettingsAndAvailability() {
		Long courseId = 101L;
		Long instructorId = 50L;

		DoubtSessionSettingsDto settings = doubtSessionService.getOrCreateSettings(courseId, instructorId);
		assertNotNull(settings);
		assertEquals(600, settings.hourlyRatePgc());
		assertFalse(settings.enabled());

		UpdateDoubtSessionSettingsRequest updateReq = new UpdateDoubtSessionSettingsRequest(true, true, 800);
		DoubtSessionSettingsDto updated = doubtSessionService.updateSettings(courseId, instructorId, updateReq);
		assertTrue(updated.enabled());
		assertTrue(updated.available());
		assertEquals(800, updated.hourlyRatePgc());

		CourseDoubtSessionAvailabilityDto avail = doubtSessionService.getCourseAvailability(courseId, 99L);
		assertTrue(avail.enabled());
		assertTrue(avail.available());
		assertEquals(800, avail.hourlyRatePgc());
		assertTrue(avail.isEnrolled());
	}

	@Test
	void testSessionBookingAndOutboxEvent() {
		Long courseId = 202L;
		Long instructorId = 60L;
		Long studentId = 70L;

		// Configure course as enabled and available
		doubtSessionService.updateSettings(courseId, instructorId, new UpdateDoubtSessionSettingsRequest(true, true, 600));

		// Student requests 15 min session (at 600 PGC/hr -> 150 PGC)
		CreateDoubtSessionRequest request = new CreateDoubtSessionRequest(courseId, 15);
		DoubtSessionDto session = doubtSessionService.requestSession(studentId, request);
		assertNotNull(session);
		assertEquals(DoubtSessionStatus.REQUESTED, session.status());
		assertEquals(0, new BigDecimal("150.00").compareTo(session.reservedAmount()));

		// Verify Outbox Event created
		var outboxEvents = outboxEventRepository.findAll();
		boolean found = outboxEvents.stream().anyMatch(e ->
				e.getTopic().equals("peergeek.doubtsession.session-booked.v1") &&
				e.getPayload().contains(String.valueOf(instructorId)));
		assertTrue(found, "Outbox event for session-booked must be persisted");
	}

	@Test
	void testSessionAcceptanceAndLiveKitToken() {
		Long courseId = 303L;
		Long instructorId = 80L;
		Long studentId = 90L;

		doubtSessionService.updateSettings(courseId, instructorId, new UpdateDoubtSessionSettingsRequest(true, true, 600));
		DoubtSessionDto session = doubtSessionService.requestSession(studentId, new CreateDoubtSessionRequest(courseId, 10));

		// Instructor accepts
		DoubtSessionDto accepted = doubtSessionService.acceptSession(instructorId, session.sessionUid());
		assertEquals(DoubtSessionStatus.ACCEPTED, accepted.status());
		assertNotNull(accepted.connectDeadlineAt());

		// Generate LiveKit token for student
		LiveKitTokenDto tokenDto = doubtSessionService.getJoinToken(studentId, session.sessionUid());
		assertNotNull(tokenDto);
		assertNotNull(tokenDto.token());
		assertEquals(session.livekitRoomName(), tokenDto.roomName());
	}

	@Test
	void testParticipantJoinLeaveAndCompletionSettlement() {
		Long courseId = 404L;
		Long instructorId = 110L;
		Long studentId = 120L;

		doubtSessionService.updateSettings(courseId, instructorId, new UpdateDoubtSessionSettingsRequest(true, true, 600));
		DoubtSessionDto session = doubtSessionService.requestSession(studentId, new CreateDoubtSessionRequest(courseId, 30));
		doubtSessionService.acceptSession(instructorId, session.sessionUid());

		// Student joins
		doubtSessionService.handleParticipantJoined(session.livekitRoomName(), studentId.toString());
		// Instructor joins -> transitions to IN_PROGRESS
		doubtSessionService.handleParticipantJoined(session.livekitRoomName(), instructorId.toString());

		DoubtSessionEntity activeSession = sessionRepository.findBySessionUid(session.sessionUid()).orElseThrow();
		assertEquals(DoubtSessionStatus.IN_PROGRESS, activeSession.getStatus());
		assertNotNull(activeSession.getStartedAt());

		// Simulate participant left
		doubtSessionService.handleParticipantLeft(session.livekitRoomName(), studentId.toString());

		// Participant ends session
		DoubtSessionDto ended = doubtSessionService.endSession(instructorId, session.sessionUid());
		assertEquals(DoubtSessionStatus.COMPLETED, ended.status());
		assertEquals("INSTRUCTOR_ENDED", ended.endReason());
		assertNotNull(ended.finalCharge());
		assertNotNull(ended.refundedAmount());
	}

	@Test
	void testSessionCancellation() {
		Long courseId = 505L;
		Long instructorId = 130L;
		Long studentId = 140L;

		doubtSessionService.updateSettings(courseId, instructorId, new UpdateDoubtSessionSettingsRequest(true, true, 600));
		DoubtSessionDto session = doubtSessionService.requestSession(studentId, new CreateDoubtSessionRequest(courseId, 5));

		// Student cancels
		DoubtSessionDto cancelled = doubtSessionService.cancelSession(studentId, session.sessionUid());
		assertEquals(DoubtSessionStatus.CANCELLED, cancelled.status());
		assertEquals("STUDENT_CANCELLED", cancelled.endReason());
		assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(cancelled.finalCharge()));
		assertEquals(0, session.reservedAmount().compareTo(cancelled.refundedAmount()));
	}
}

