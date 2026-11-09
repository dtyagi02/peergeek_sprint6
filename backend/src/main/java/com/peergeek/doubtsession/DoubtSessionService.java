package com.peergeek.doubtsession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.peergeek.course.CourseEntity;
import com.peergeek.course.CourseRepository;
import com.peergeek.course.CourseStatus;
import com.peergeek.doubtsession.dto.CourseDoubtSessionAvailabilityDto;
import com.peergeek.doubtsession.dto.CreateDoubtSessionRequest;
import com.peergeek.doubtsession.dto.DoubtSessionDto;
import com.peergeek.doubtsession.dto.DoubtSessionSettingsDto;
import com.peergeek.doubtsession.dto.LiveKitTokenDto;
import com.peergeek.doubtsession.dto.UpdateDoubtSessionSettingsRequest;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.wallet.WalletService;
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
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final WalletService walletService;
	private final LiveKitService liveKitService;
	private final NotificationService notificationService;

	public DoubtSessionService(
			DoubtSessionRepository sessionRepository,
			CourseDoubtSessionSettingsRepository settingsRepository,
			CourseRepository courseRepository,
			UserRepository userRepository,
			EnrollmentRepository enrollmentRepository,
			WalletService walletService,
			LiveKitService liveKitService,
			NotificationService notificationService) {
		this.sessionRepository = sessionRepository;
		this.settingsRepository = settingsRepository;
		this.courseRepository = courseRepository;
		this.userRepository = userRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.walletService = walletService;
		this.liveKitService = liveKitService;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public CourseDoubtSessionSettingsEntity getSettingsEntity(Long courseId) {
		return settingsRepository.findByCourseId(courseId)
				.orElse(null);
	}

	@Transactional
	public DoubtSessionSettingsDto getOrCreateSettings(Long courseId, Long instructorUserId) {
		CourseEntity course = validateCourseOwnership(courseId, instructorUserId);
		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(courseId)
				.orElseGet(() -> {
					CourseDoubtSessionSettingsEntity created =
							new CourseDoubtSessionSettingsEntity(course, course.getOwner(), false, false, 600);
					return settingsRepository.save(created);
				});
		return toSettingsDto(settings);
	}

	@Transactional
	public DoubtSessionSettingsDto updateSettings(
			Long courseId,
			Long instructorUserId,
			UpdateDoubtSessionSettingsRequest request) {
		CourseEntity course = validateCourseOwnership(courseId, instructorUserId);
		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(courseId)
				.orElseGet(() -> new CourseDoubtSessionSettingsEntity(course, course.getOwner()));

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
		CourseEntity course = courseRepository.findById(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

		if (course.getStatus() != CourseStatus.PUBLISHED || course.getDeletedAt() != null) {
			return new CourseDoubtSessionAvailabilityDto(
					courseId, false, false, 0,
					course.getOwner().getDisplayName(), false, false,
					course.getOwner().getId(),
					currentUserIdOrNull != null && currentUserIdOrNull.equals(course.getOwner().getId()));
		}

		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(courseId).orElse(null);
		boolean enabled = settings != null && settings.isEnabled();
		boolean available = settings != null && settings.isAvailable();
		int hourlyRate = settings != null ? settings.getHourlyRatePgc() : 600;

		Long instructorId = course.getOwner().getId();
		boolean instructorHasActiveSession = sessionRepository.existsByInstructorIdAndStatusIn(instructorId, ACTIVE_STATUSES);

		boolean isEnrolled = false;
		if (currentUserIdOrNull != null) {
			isEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
					currentUserIdOrNull, courseId, EnrollmentStatus.ACTIVE);
		}

		boolean isOwner = currentUserIdOrNull != null && currentUserIdOrNull.equals(instructorId);

		return new CourseDoubtSessionAvailabilityDto(
				courseId,
				enabled,
				available,
				hourlyRate,
				course.getOwner().getDisplayName(),
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
		boolean enrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
				studentUserId, request.courseId(), EnrollmentStatus.ACTIVE);
		if (!enrolled) {
			throw new ForbiddenException("You must be actively enrolled in this course to request doubt clearing.");
		}

		// 2. Student single active session check
		if (sessionRepository.existsByStudentIdAndStatusIn(studentUserId, ACTIVE_STATUSES)) {
			throw new ConflictException("You already have an active doubt session in progress or requested.");
		}

		// 3. Course and instructor availability check
		CourseEntity course = courseRepository.findById(request.courseId())
				.orElseThrow(() -> new NotFoundException("Course not found: " + request.courseId()));

		if (course.getStatus() != CourseStatus.PUBLISHED || course.getDeletedAt() != null) {
			throw new BadRequestException("Live doubt clearing is not available for this course.");
		}

		UserEntity instructor = course.getOwner();
		if (instructor.getId().equals(studentUserId)) {
			throw new BadRequestException("Instructors cannot request doubt clearing for their own course.");
		}

		CourseDoubtSessionSettingsEntity settings = settingsRepository.findByCourseId(request.courseId()).orElse(null);
		if (settings == null || !settings.isEnabled() || !settings.isAvailable()) {
			throw new BadRequestException("The instructor is currently unavailable for live doubt clearing.");
		}

		// 4. Instructor single active session check
		if (sessionRepository.existsByInstructorIdAndStatusIn(instructor.getId(), ACTIVE_STATUSES)) {
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
		UserEntity student = userRepository.findById(studentUserId)
				.orElseThrow(() -> new NotFoundException("Student not found: " + studentUserId));

		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(60); // 60s request response timeout
		String roomName = "doubt-" + sessionUid;

		DoubtSessionEntity session = new DoubtSessionEntity(
				sessionUid,
				course,
				instructor,
				student,
				hourlyRate,
				request.maxDurationMinutes(),
				maxCost,
				now,
				expiresAt,
				roomName);

		DoubtSessionEntity saved = sessionRepository.save(session);

		// 7. Notify instructor
		try {
			notificationService.createNotification(
					instructor.getId(),
					NotificationType.REPORT_CREATED,
					"Incoming Doubt Session Request",
					student.getDisplayName() + " requested a " + request.maxDurationMinutes() + "-minute live doubt clearing session.",
					"DOUBT_SESSION",
					saved.getId(),
					"/instructor/doubt-sessions",
					"doubt_req_" + sessionUid);
		} catch (Exception ex) {
			log.warn("Could not dispatch notification to instructor {}: {}", instructor.getId(), ex.getMessage());
		}

		return DoubtSessionDto.from(saved);
	}

	private DoubtSessionEntity findSession(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			throw new NotFoundException("Session not found: " + identifier);
		}
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
		if (identifier == null || identifier.isBlank()) {
			throw new NotFoundException("Session not found: " + identifier);
		}
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

	@Transactional
	public DoubtSessionDto acceptSession(Long instructorUserId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		if (!session.getInstructor().getId().equals(instructorUserId)) {
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

		// Ensure instructor does not already have another active call in progress
		List<DoubtSessionEntity> existing = sessionRepository.findActiveByInstructorId(instructorUserId,
				Set.of(DoubtSessionStatus.ACCEPTED, DoubtSessionStatus.IN_PROGRESS));
		if (!existing.isEmpty() && !existing.get(0).getSessionUid().equals(session.getSessionUid())) {
			throw new ConflictException("You already have an active call in progress.");
		}

		session.setStatus(DoubtSessionStatus.ACCEPTED);
		session.setAcceptedAt(now);
		session.setConnectDeadlineAt(now.plusSeconds(120)); // 2-minute connection deadline
		DoubtSessionEntity saved = sessionRepository.save(session);

		// Notify student
		try {
			notificationService.createNotification(
					session.getStudent().getId(),
					NotificationType.REPORT_CREATED,
					"Doubt Session Accepted",
					session.getInstructor().getDisplayName() + " accepted your doubt session request! Connect now.",
					"DOUBT_SESSION",
					saved.getId(),
					"/doubt-sessions/" + saved.getSessionUid(),
					"doubt_acc_" + saved.getSessionUid());
		} catch (Exception ex) {
			log.warn("Could not notify student of accepted session: {}", ex.getMessage());
		}

		return DoubtSessionDto.from(saved);
	}

	@Transactional
	public DoubtSessionDto rejectSession(Long instructorUserId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		if (!session.getInstructor().getId().equals(instructorUserId)) {
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

		if (!session.getStudent().getId().equals(studentUserId)) {
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

		if (!session.getStudent().getId().equals(userId) && !session.getInstructor().getId().equals(userId)) {
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

		boolean isStudent = session.getStudent().getId().equals(userId);
		boolean isInstructor = session.getInstructor().getId().equals(userId);

		if (!isStudent && !isInstructor) {
			throw new ForbiddenException("You are not an authorized participant in this session.");
		}

		if (session.getStatus() != DoubtSessionStatus.ACCEPTED && session.getStatus() != DoubtSessionStatus.IN_PROGRESS) {
			throw new BadRequestException("Session is not in an active connectable state (status: " + session.getStatus() + ")");
		}

		UserEntity user = isStudent ? session.getStudent() : session.getInstructor();
		String token = liveKitService.createJoinToken(
				session.getLivekitRoomName(),
				user.getId().toString(),
				user.getDisplayName(),
				session.getMaxDurationMinutes());

		return new LiveKitTokenDto(token, liveKitService.getLiveKitUrl(), session.getLivekitRoomName());
	}

	@Transactional
	public DoubtSessionDto endSession(Long userId, String sessionUid) {
		DoubtSessionEntity session = findSessionWithLock(sessionUid);

		boolean isStudent = session.getStudent().getId().equals(userId);
		boolean isInstructor = session.getInstructor().getId().equals(userId);

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

		Instant now = Instant.now();
		if (participantIdentity.equals(session.getStudent().getId().toString())) {
			session.setStudentConnected(true);
		} else if (participantIdentity.equals(session.getInstructor().getId().toString())) {
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
		if (session == null || session.getStatus() != DoubtSessionStatus.IN_PROGRESS) return;

		session = sessionRepository.findWithLockBySessionUid(session.getSessionUid()).orElse(null);
		if (session == null || session.getStatus() != DoubtSessionStatus.IN_PROGRESS) return;

		Instant now = Instant.now();
		// Accumulate connected time while both were in call
		if (session.isStudentConnected() && session.isInstructorConnected() && session.getLastBothConnectedAt() != null) {
			long elapsed = Math.max(0, now.getEpochSecond() - session.getLastBothConnectedAt().getEpochSecond());
			session.setBillableSeconds(session.getBillableSeconds() + (int) elapsed);
		}

		if (participantIdentity.equals(session.getStudent().getId().toString())) {
			session.setStudentConnected(false);
		} else if (participantIdentity.equals(session.getInstructor().getId().toString())) {
			session.setInstructorConnected(false);
		}

		session.setLastBothConnectedAt(null);
		session.setLastDisconnectedAt(now);
		session.setReconnectDeadlineAt(now.plusSeconds(60)); // 60s reconnection window

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
		// Accumulate any ongoing connected seconds
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
		Long studentId = session.getStudent().getId();
		Long instructorId = session.getInstructor().getId();
		String sessionUid = session.getSessionUid();

		if (session.getStartedAt() == null || billableSeconds <= 0) {
			// Call never started or no billable time: release full reservation
			session.setFinalCharge(BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY));
			session.setRefundedAmount(reserved);
			walletService.releaseReservedFunds(
					studentId,
					reserved,
					"DOUBT_SESSION",
					sessionUid,
					"Call never started (" + endReason + "); full reservation released");
		} else {
			// Round up to whole minute
			int billableMinutes = (billableSeconds + 59) / 60;
			billableMinutes = Math.min(billableMinutes, session.getMaxDurationMinutes());

			double hourlyRate = session.getHourlyRatePgc();
			double cost = (billableMinutes / 60.0) * hourlyRate;
			// Round charge
			BigDecimal charge = BigDecimal.valueOf(Math.round(cost * 100.0) / 100.0)
					.setScale(2, RoundingMode.HALF_UP)
					.min(reserved); // never exceed reserved
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

		// 1. Expire unaccepted requests after 60s
		List<DoubtSessionEntity> expiredRequests =
				sessionRepository.findByStatusAndExpiresAtBefore(DoubtSessionStatus.REQUESTED, now);
		for (DoubtSessionEntity s : expiredRequests) {
			finalizeSession(s, DoubtSessionStatus.EXPIRED, "REQUEST_EXPIRED");
		}

		// 2. Expire accepted calls where participants failed to connect within 2m
		List<DoubtSessionEntity> connectTimeouts =
				sessionRepository.findByStatusAndConnectDeadlineAtBefore(DoubtSessionStatus.ACCEPTED, now);
		for (DoubtSessionEntity s : connectTimeouts) {
			finalizeSession(s, DoubtSessionStatus.EXPIRED, "CONNECT_TIMEOUT");
		}

		// 3. Reconnection timeout (60 seconds)
		List<DoubtSessionEntity> reconnectTimeouts =
				sessionRepository.findReconnectTimedOut(DoubtSessionStatus.IN_PROGRESS, now);
		for (DoubtSessionEntity s : reconnectTimeouts) {
			finalizeSession(s, DoubtSessionStatus.INTERRUPTED, "RECONNECTION_TIMEOUT");
		}

		// 4. Max duration enforcement
		List<DoubtSessionEntity> inProgress = sessionRepository.findAll()
				.stream()
				.filter(s -> s.getStatus() == DoubtSessionStatus.IN_PROGRESS)
				.toList();
		for (DoubtSessionEntity s : inProgress) {
			int currentSeconds = s.getBillableSeconds();
			if (s.getLastBothConnectedAt() != null) {
				currentSeconds += (int) (now.getEpochSecond() - s.getLastBothConnectedAt().getEpochSecond());
			}
			if (currentSeconds >= s.getMaxDurationMinutes() * 60) {
				finalizeSession(s, DoubtSessionStatus.COMPLETED, "MAX_DURATION_REACHED");
			}
		}
	}

	private CourseEntity validateCourseOwnership(Long courseId, Long instructorUserId) {
		CourseEntity course = courseRepository.findById(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

		if (!course.getOwner().getId().equals(instructorUserId)) {
			throw new ForbiddenException("Only the instructor who owns this course can manage its doubt clearing settings.");
		}
		if (course.getStatus() != CourseStatus.PUBLISHED || course.getDeletedAt() != null) {
			throw new BadRequestException("Doubt clearing can only be configured for published courses.");
		}
		return course;
	}

	private DoubtSessionSettingsDto toSettingsDto(CourseDoubtSessionSettingsEntity e) {
		return new DoubtSessionSettingsDto(
				e.getCourse().getId(),
				e.isEnabled(),
				e.isAvailable(),
				e.getHourlyRatePgc());
	}
}
