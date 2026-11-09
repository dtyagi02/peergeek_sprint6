package com.peergeek.doubtsession.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.peergeek.doubtsession.client.CourseLearningClient;
import com.peergeek.doubtsession.client.WalletServiceClient;
import com.peergeek.doubtsession.dto.*;
import com.peergeek.doubtsession.entity.CourseDoubtSessionSettingsEntity;
import com.peergeek.doubtsession.entity.DoubtSessionEntity;
import com.peergeek.doubtsession.entity.DoubtSessionStatus;
import com.peergeek.doubtsession.exception.BadRequestException;
import com.peergeek.doubtsession.exception.ConflictException;
import com.peergeek.doubtsession.exception.ForbiddenException;
import com.peergeek.doubtsession.exception.NotFoundException;
import com.peergeek.doubtsession.livekit.LiveKitService;
import com.peergeek.doubtsession.outbox.OutboxEventWriter;
import com.peergeek.doubtsession.repository.CourseDoubtSessionSettingsRepository;
import com.peergeek.doubtsession.repository.DoubtSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoubtSessionService {
	private static final Logger log = LoggerFactory.getLogger(DoubtSessionService.class);

	public static final Set<Integer> ALLOWED_DURATIONS = Set.of(5, 10, 15, 30);
	public static final Set<DoubtSessionStatus> ACTIVE_STATUSES =
			Set.of(DoubtSessionStatus.REQUESTED, DoubtSessionStatus.ACCEPTED, DoubtSessionStatus.IN_PROGRESS);

	private final DoubtSessionRepository sessionRepository;
	private final CourseDoubtSessionSettingsRepository settingsRepository;
	private final WalletServiceClient walletService;
	private final CourseLearningClient courseLearningClient;
	private final LiveKitService liveKitService;
	private final OutboxEventWriter outboxEventWriter;

	public DoubtSessionService(
			DoubtSessionRepository sessionRepository,
			CourseDoubtSessionSettingsRepository settingsRepository,
			WalletServiceClient walletService,
			CourseLearningClient courseLearningClient,
			LiveKitService liveKitService,
			OutboxEventWriter outboxEventWriter) {
		this.sessionRepository = sessionRepository;
		this.settingsRepository = settingsRepository;
		this.walletService = walletService;
		this.courseLearningClient = courseLearningClient;
		this.liveKitService = liveKitService;
		this.outboxEventWriter = outboxEventWriter;
	}

	@Transactional(readOnly = true)
	public CourseDoubtSessionSettingsEntity getSettingsEntity(Long courseId) {
		return settingsRepository.findByCourseId(courseId).orElse(null);
	}

	@Transactional
	public DoubtSessionSettingsDto getOrCreateSettings(Long courseId, Long instructorUserId) {
		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(courseId)
				.orElseGet(() -> {
					CourseDoubtSessionSettingsEntity created =
							new CourseDoubtSessionSettingsEntity(courseId, instructorUserId, false, false, 600);
					return settingsRepository.save(created);
				});
		return toSettingsDto(settings);
	}

	@Transactional
	public DoubtSessionSettingsDto updateSettings(
			Long courseId,
			Long instructorUserId,
			UpdateDoubtSessionSettingsRequest request) {
		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(courseId)
				.orElseGet(() -> new CourseDoubtSessionSettingsEntity(courseId, instructorUserId));

		if (request.enabled() != null) {
			settings.setEnabled(request.enabled());
		}
		if (request.available() != null) {
			settings.setAvailable(request.available());
		}
		if (request.hourlyRatePgc() != null) {
			if (request.hourlyRatePgc() < 1) {
				throw new BadRequestException("Hourly rate must be at least 1 GeekCoin");
			}
			settings.setHourlyRatePgc(request.hourlyRatePgc());
		}

		CourseDoubtSessionSettingsEntity saved = settingsRepository.save(settings);
		return toSettingsDto(saved);
	}

	@Transactional
	public void updateInstructorAvailability(Long instructorUserId, boolean available) {
		List<CourseDoubtSessionSettingsEntity> allSettings = settingsRepository.findByInstructorId(instructorUserId);
		for (CourseDoubtSessionSettingsEntity s : allSettings) {
			s.setAvailable(available);
			settingsRepository.save(s);
		}
	}

	@Transactional(readOnly = true)
	public CourseDoubtSessionAvailabilityDto getCourseAvailability(Long courseId, Long currentUserIdOrNull) {
		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(courseId).orElse(null);
		boolean enabled = settings != null && settings.isEnabled();
		boolean available = settings != null && settings.isAvailable();
		int hourlyRate = settings != null ? settings.getHourlyRatePgc() : 600;
		Long instructorId = settings != null ? settings.getInstructorId() : 0L;

		boolean instructorHasActiveSession = instructorId > 0 &&
				sessionRepository.existsByInstructorIdAndStatusIn(instructorId, ACTIVE_STATUSES);

		boolean isEnrolled = false;
		if (currentUserIdOrNull != null) {
			isEnrolled = courseLearningClient.isStudentEnrolled(courseId, currentUserIdOrNull);
		}

		boolean isOwner = currentUserIdOrNull != null && currentUserIdOrNull.equals(instructorId);

		return new CourseDoubtSessionAvailabilityDto(
				courseId,
				enabled,
				available,
				hourlyRate,
				"Instructor #" + instructorId,
				instructorHasActiveSession,
				isEnrolled,
				instructorId,
				isOwner);
	}

	@Transactional
	public DoubtSessionDto requestSession(Long studentUserId, CreateDoubtSessionRequest request) {
		if (request.courseId() == null) {
			throw new BadRequestException("courseId is required");
		}
		if (!ALLOWED_DURATIONS.contains(request.maxDurationMinutes())) {
			throw new BadRequestException("Invalid duration. Allowed durations are 5, 10, 15, or 30 minutes.");
		}

		// 1. Enrollment check
		boolean enrolled = courseLearningClient.isStudentEnrolled(request.courseId(), studentUserId);
		if (!enrolled) {
			throw new ForbiddenException("You must be actively enrolled in this course to request doubt clearing.");
		}

		// 2. Student single active session check
		if (sessionRepository.existsByStudentIdAndStatusIn(studentUserId, ACTIVE_STATUSES)) {
			throw new ConflictException("You already have an active doubt session in progress or requested.");
		}

		// 3. Settings check
		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(request.courseId()).orElse(null);
		if (settings == null || !settings.isEnabled() || !settings.isAvailable()) {
			throw new BadRequestException("The instructor is currently unavailable for live doubt clearing.");
		}

		Long instructorId = settings.getInstructorId();
		if (instructorId.equals(studentUserId)) {
			throw new BadRequestException("Instructors cannot request doubt clearing for their own course.");
		}

		// 4. Instructor single active session check
		if (sessionRepository.existsByInstructorIdAndStatusIn(instructorId, ACTIVE_STATUSES)) {
			throw new ConflictException("The instructor is currently busy with another doubt clearing session. Please try again shortly.");
		}

		// 5. Calculate and reserve maximum session cost
		int hourlyRate = settings.getHourlyRatePgc();
		double durationHours = (double) request.maxDurationMinutes() / 60.0;
		long costCoins = (long) Math.ceil(hourlyRate * durationHours);
		BigDecimal maxCost = BigDecimal.valueOf(costCoins).setScale(2, RoundingMode.HALF_UP);

		String sessionUid = UUID.randomUUID().toString();
		walletService.reserveFunds(
				studentUserId,
				maxCost,
				"DOUBT_SESSION",
				sessionUid,
				"Reservation for live session (" + request.maxDurationMinutes() + " mins @ " + hourlyRate + " PGC/h)");

		// 6. Create session
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(60); // 60s request response timeout
		String roomName = "doubt-" + sessionUid;

		DoubtSessionEntity session = new DoubtSessionEntity(
				sessionUid,
				request.courseId(),
				instructorId,
				studentUserId,
				hourlyRate,
				request.maxDurationMinutes(),
				maxCost,
				now,
				expiresAt,
				roomName);

		DoubtSessionEntity saved = sessionRepository.save(session);

		// 7. Persist transactional outbox event
		outboxEventWriter.writeEvent(
				"peergeek.doubtsession.session-booked.v1",
				"DoubtSessionBooked",
				Map.of(
						"instructorId", instructorId,
						"sessionId", saved.getId(),
						"sessionTitle", "Live Doubt Session (" + request.maxDurationMinutes() + " min)",
						"studentId", studentUserId,
						"courseId", request.courseId()));

		return DoubtSessionDto.from(saved);
	}

	@Transactional
	public DoubtSessionDto acceptSession(Long instructorUserId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		if (!session.getInstructorId().equals(instructorUserId)) {
			throw new ForbiddenException("Only the designated instructor can accept this session request.");
		}

		if (session.getStatus() != DoubtSessionStatus.REQUESTED) {
			throw new BadRequestException("Session is not in REQUESTED status (current: " + session.getStatus() + ")");
		}

		Instant now = Instant.now();
		if (now.isAfter(session.getExpiresAt())) {
			finalizeSession(session, DoubtSessionStatus.EXPIRED, "REQUEST_EXPIRED");
			throw new BadRequestException("This session request has expired.");
		}

		List<DoubtSessionEntity> existing = sessionRepository.findActiveByInstructorId(instructorUserId,
				Set.of(DoubtSessionStatus.ACCEPTED, DoubtSessionStatus.IN_PROGRESS));
		if (!existing.isEmpty() && !existing.get(0).getSessionUid().equals(session.getSessionUid())) {
			throw new ConflictException("You already have an active call in progress.");
		}

		session.setStatus(DoubtSessionStatus.ACCEPTED);
		session.setAcceptedAt(now);
		session.setConnectDeadlineAt(now.plusSeconds(120)); // 2-minute connection deadline
		DoubtSessionEntity saved = sessionRepository.save(session);

		return DoubtSessionDto.from(saved);
	}

	@Transactional
	public DoubtSessionDto rejectSession(Long instructorUserId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		if (!session.getInstructorId().equals(instructorUserId)) {
			throw new ForbiddenException("Only the designated instructor can reject this session request.");
		}

		if (session.getStatus() != DoubtSessionStatus.REQUESTED) {
			throw new BadRequestException("Cannot reject session in status: " + session.getStatus());
		}

		finalizeSession(session, DoubtSessionStatus.REJECTED, "INSTRUCTOR_REJECTED");
		return DoubtSessionDto.from(session);
	}

	@Transactional
	public DoubtSessionDto cancelSession(Long studentUserId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		if (!session.getStudentId().equals(studentUserId)) {
			throw new ForbiddenException("Only the requesting student can cancel this session.");
		}

		if (session.getStatus() != DoubtSessionStatus.REQUESTED) {
			throw new BadRequestException("Sessions can only be cancelled while in REQUESTED status.");
		}

		finalizeSession(session, DoubtSessionStatus.CANCELLED, "STUDENT_CANCELLED");
		return DoubtSessionDto.from(session);
	}

	@Transactional(readOnly = true)
	public DoubtSessionDto getSession(Long userId, String sessionUid) {
		DoubtSessionEntity session = findSession(sessionUid);

		if (!session.getStudentId().equals(userId) && !session.getInstructorId().equals(userId)) {
			throw new ForbiddenException("You are not a participant in this doubt session.");
		}

		return DoubtSessionDto.from(session);
	}

	@Transactional(readOnly = true)
	public DoubtSessionDto getActiveSessionForUser(Long userId) {
		List<DoubtSessionEntity> studentActive = sessionRepository.findActiveByStudentId(userId, ACTIVE_STATUSES);
		if (!studentActive.isEmpty()) {
			return DoubtSessionDto.from(studentActive.get(0));
		}
		List<DoubtSessionEntity> instructorActive = sessionRepository.findActiveByInstructorId(userId, ACTIVE_STATUSES);
		if (!instructorActive.isEmpty()) {
			return DoubtSessionDto.from(instructorActive.get(0));
		}
		return null;
	}

	@Transactional(readOnly = true)
	public DoubtSessionDto getActiveInstructorRequest(Long instructorUserId) {
		List<DoubtSessionEntity> active = sessionRepository.findActiveByInstructorId(instructorUserId, ACTIVE_STATUSES);
		return active.isEmpty() ? null : DoubtSessionDto.from(active.get(0));
	}

	@Transactional
	public LiveKitTokenDto getJoinToken(Long userId, String sessionUid) {
		DoubtSessionEntity session = findSession(sessionUid);

		boolean isStudent = session.getStudentId().equals(userId);
		boolean isInstructor = session.getInstructorId().equals(userId);

		if (!isStudent && !isInstructor) {
			throw new ForbiddenException("You are not an authorized participant in this session.");
		}

		if (session.getStatus() != DoubtSessionStatus.ACCEPTED && session.getStatus() != DoubtSessionStatus.IN_PROGRESS) {
			throw new BadRequestException("Session is not in an active connectable state (status: " + session.getStatus() + ")");
		}

		String identity = userId.toString();
		String name = isStudent ? "Student #" + userId : "Instructor #" + userId;
		String token = liveKitService.createJoinToken(
				session.getLivekitRoomName(),
				identity,
				name,
				session.getMaxDurationMinutes());

		return new LiveKitTokenDto(token, liveKitService.getLiveKitUrl(), session.getLivekitRoomName());
	}

	@Transactional
	public DoubtSessionDto endSession(Long userId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		boolean isStudent = session.getStudentId().equals(userId);
		boolean isInstructor = session.getInstructorId().equals(userId);

		if (!isStudent && !isInstructor) {
			throw new ForbiddenException("You are not an authorized participant in this session.");
		}

		if (session.getStatus().isTerminal()) {
			return DoubtSessionDto.from(session);
		}

		String reason = isInstructor ? "INSTRUCTOR_ENDED" : "STUDENT_ENDED";
		finalizeSession(session, DoubtSessionStatus.COMPLETED, reason);
		return DoubtSessionDto.from(session);
	}

	@Transactional
	public void handleParticipantJoined(String roomName, String participantIdentity) {
		DoubtSessionEntity session = sessionRepository.findByLivekitRoomName(roomName).orElse(null);
		if (session == null || session.getStatus().isTerminal()) return;

		session = sessionRepository.findWithLockBySessionUid(session.getSessionUid()).orElse(null);
		if (session == null || session.getStatus().isTerminal()) return;

		if (participantIdentity == null ||
				(!participantIdentity.equals(session.getStudentId().toString()) &&
				 !participantIdentity.equals(session.getInstructorId().toString()))) {
			log.warn("Ignored participant join for unauthorized identity: {} in room: {}", participantIdentity, roomName);
			return;
		}

		Instant now = Instant.now();
		boolean wasBothConnected = session.isStudentConnected() && session.isInstructorConnected() && session.getLastBothConnectedAt() != null;
		if (wasBothConnected) {
			long elapsed = Math.max(0, now.getEpochSecond() - session.getLastBothConnectedAt().getEpochSecond());
			session.setBillableSeconds(session.getBillableSeconds() + (int) elapsed);
		}

		if (participantIdentity.equals(session.getStudentId().toString())) {
			session.setStudentConnected(true);
		} else if (participantIdentity.equals(session.getInstructorId().toString())) {
			session.setInstructorConnected(true);
		}

		if (session.isStudentConnected() && session.isInstructorConnected()) {
			if (session.getStatus() == DoubtSessionStatus.ACCEPTED) {
				session.setStatus(DoubtSessionStatus.IN_PROGRESS);
				if (session.getStartedAt() == null) {
					session.setStartedAt(now);
				}
			}
			session.setLastBothConnectedAt(now);
			session.setLastDisconnectedAt(null);
			session.setReconnectDeadlineAt(null);
		}

		sessionRepository.save(session);
	}

	@Transactional
	public void handleParticipantLeft(String roomName, String participantIdentity) {
		DoubtSessionEntity session = sessionRepository.findByLivekitRoomName(roomName).orElse(null);
		if (session == null || session.getStatus().isTerminal()) return;

		session = sessionRepository.findWithLockBySessionUid(session.getSessionUid()).orElse(null);
		if (session == null || session.getStatus().isTerminal()) return;

		if (participantIdentity == null ||
				(!participantIdentity.equals(session.getStudentId().toString()) &&
				 !participantIdentity.equals(session.getInstructorId().toString()))) {
			log.warn("Ignored participant left for unauthorized identity: {} in room: {}", participantIdentity, roomName);
			return;
		}

		Instant now = Instant.now();
		if (session.isStudentConnected() && session.isInstructorConnected() && session.getLastBothConnectedAt() != null) {
			long elapsed = Math.max(0, now.getEpochSecond() - session.getLastBothConnectedAt().getEpochSecond());
			session.setBillableSeconds(session.getBillableSeconds() + (int) elapsed);
		}

		if (participantIdentity.equals(session.getStudentId().toString())) {
			session.setStudentConnected(false);
		} else if (participantIdentity.equals(session.getInstructorId().toString())) {
			session.setInstructorConnected(false);
		}

		session.setLastBothConnectedAt(null);
		session.setLastDisconnectedAt(now);
		session.setReconnectDeadlineAt(now.plusSeconds(60));

		sessionRepository.save(session);
	}

	@Transactional
	public void handleRoomFinished(String roomName) {
		DoubtSessionEntity session = sessionRepository.findByLivekitRoomName(roomName).orElse(null);
		if (session != null && !session.getStatus().isTerminal()) {
			finalizeSession(session, DoubtSessionStatus.COMPLETED, "ROOM_FINISHED");
		}
	}

	@Transactional
	public void finalizeSession(DoubtSessionEntity session, DoubtSessionStatus terminalStatus, String endReason) {
		if (session.getStatus().isTerminal()) return;

		Instant now = Instant.now();
		if (session.getStatus() == DoubtSessionStatus.IN_PROGRESS && session.getLastBothConnectedAt() != null) {
			long elapsed = Math.max(0, now.getEpochSecond() - session.getLastBothConnectedAt().getEpochSecond());
			session.setBillableSeconds(session.getBillableSeconds() + (int) elapsed);
			session.setLastBothConnectedAt(null);
		}

		session.setStatus(terminalStatus);
		session.setEndedAt(now);
		session.setEndReason(endReason);

		int billableSeconds = session.getBillableSeconds();
		int maxSeconds = session.getMaxDurationMinutes() * 60;
		if (billableSeconds > maxSeconds) {
			billableSeconds = maxSeconds;
			session.setBillableSeconds(billableSeconds);
		}

		BigDecimal reserved = session.getReservedAmount();
		Long studentId = session.getStudentId();
		Long instructorId = session.getInstructorId();
		String sessionUid = session.getSessionUid();

		if (session.getStartedAt() == null || billableSeconds <= 0) {
			session.setFinalCharge(BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY));
			session.setRefundedAmount(reserved);
			walletService.releaseReservedFunds(
					studentId,
					reserved,
					"DOUBT_SESSION",
					sessionUid,
					"Call never started (" + endReason + "); full reservation released");
		} else {
			int billableMinutes = (billableSeconds + 59) / 60;
			billableMinutes = Math.min(billableMinutes, session.getMaxDurationMinutes());

			double hourlyRate = session.getHourlyRatePgc();
			double cost = (billableMinutes / 60.0) * hourlyRate;
			BigDecimal charge = BigDecimal.valueOf(Math.round(cost * 100.0) / 100.0)
					.setScale(2, RoundingMode.HALF_UP)
					.min(reserved);
			BigDecimal refund = reserved.subtract(charge);

			session.setFinalCharge(charge);
			session.setRefundedAmount(refund);

			walletService.settleSessionPayment(
					studentId,
					instructorId,
					reserved,
					charge,
					"DOUBT_SESSION",
					sessionUid,
					"Doubt session billing: " + billableMinutes + "m @ " + session.getHourlyRatePgc() + " PGC/h (" + endReason + ")");
		}

		sessionRepository.save(session);
		log.info("Finalized session uid={} status={} reason={} billableSeconds={} charge={} refunded={}",
				sessionUid, terminalStatus, endReason, billableSeconds, session.getFinalCharge(), session.getRefundedAmount());
	}

	@Transactional
	public void processTimeouts() {
		Instant now = Instant.now();

		List<DoubtSessionEntity> expiredRequests =
				sessionRepository.findByStatusAndExpiresAtBefore(DoubtSessionStatus.REQUESTED, now);
		for (DoubtSessionEntity s : expiredRequests) {
			finalizeSession(s, DoubtSessionStatus.EXPIRED, "REQUEST_EXPIRED");
		}

		List<DoubtSessionEntity> connectTimeouts =
				sessionRepository.findByStatusAndConnectDeadlineAtBefore(DoubtSessionStatus.ACCEPTED, now);
		for (DoubtSessionEntity s : connectTimeouts) {
			finalizeSession(s, DoubtSessionStatus.EXPIRED, "CONNECT_TIMEOUT");
		}

		List<DoubtSessionEntity> reconnectTimeouts =
				sessionRepository.findReconnectTimedOut(DoubtSessionStatus.IN_PROGRESS, now);
		for (DoubtSessionEntity s : reconnectTimeouts) {
			finalizeSession(s, DoubtSessionStatus.INTERRUPTED, "RECONNECT_TIMEOUT");
		}
	}

	private DoubtSessionEntity findSession(String identifier) {
		try {
			Long id = Long.parseLong(identifier);
			return sessionRepository.findById(id)
					.orElseGet(() -> sessionRepository.findBySessionUid(identifier)
							.orElseThrow(() -> new NotFoundException("Session not found: " + identifier)));
		} catch (NumberFormatException ignored) {
			return sessionRepository.findBySessionUid(identifier)
					.orElseThrow(() -> new NotFoundException("Session not found: " + identifier));
		}
	}

	private DoubtSessionEntity findSessionWithLock(String identifier) {
		try {
			Long id = Long.parseLong(identifier);
			return sessionRepository.findWithLockById(id)
					.orElseGet(() -> sessionRepository.findWithLockBySessionUid(identifier)
							.orElseThrow(() -> new NotFoundException("Session not found: " + identifier)));
		} catch (NumberFormatException ignored) {
			return sessionRepository.findWithLockBySessionUid(identifier)
					.orElseThrow(() -> new NotFoundException("Session not found: " + identifier));
		}
	}

	private DoubtSessionSettingsDto toSettingsDto(CourseDoubtSessionSettingsEntity s) {
		return new DoubtSessionSettingsDto(s.getCourseId(), s.isEnabled(), s.isAvailable(), s.getHourlyRatePgc());
	}
}

