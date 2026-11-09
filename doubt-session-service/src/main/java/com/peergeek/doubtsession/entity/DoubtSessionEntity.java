package com.peergeek.doubtsession.entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "doubt_sessions")
public class DoubtSessionEntity extends BaseEntity {

	@Column(name = "session_uid", nullable = false, unique = true, length = 36)
	private String sessionUid;

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "instructor_id", nullable = false)
	private Long instructorId;

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DoubtSessionStatus status = DoubtSessionStatus.REQUESTED;

	@Column(name = "hourly_rate_pgc", nullable = false)
	private int hourlyRatePgc;

	@Column(name = "max_duration_minutes", nullable = false)
	private int maxDurationMinutes;

	@Column(name = "reserved_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal reservedAmount;

	@Column(name = "final_charge", precision = 19, scale = 2)
	private BigDecimal finalCharge;

	@Column(name = "refunded_amount", precision = 19, scale = 2)
	private BigDecimal refundedAmount;

	@Column(name = "billable_seconds", nullable = false)
	private int billableSeconds = 0;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "connect_deadline_at")
	private Instant connectDeadlineAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "livekit_room_name", nullable = false, length = 128)
	private String livekitRoomName;

	@Column(name = "student_connected", nullable = false)
	private boolean studentConnected = false;

	@Column(name = "instructor_connected", nullable = false)
	private boolean instructorConnected = false;

	@Column(name = "last_both_connected_at")
	private Instant lastBothConnectedAt;

	@Column(name = "last_disconnected_at")
	private Instant lastDisconnectedAt;

	@Column(name = "reconnect_deadline_at")
	private Instant reconnectDeadlineAt;

	@Column(name = "end_reason", length = 64)
	private String endReason;

	public DoubtSessionEntity() {
	}

	public DoubtSessionEntity(
			String sessionUid,
			Long courseId,
			Long instructorId,
			Long studentId,
			int hourlyRatePgc,
			int maxDurationMinutes,
			BigDecimal reservedAmount,
			Instant requestedAt,
			Instant expiresAt,
			String livekitRoomName) {
		this.sessionUid = sessionUid;
		this.courseId = courseId;
		this.instructorId = instructorId;
		this.studentId = studentId;
		this.status = DoubtSessionStatus.REQUESTED;
		this.hourlyRatePgc = hourlyRatePgc;
		this.maxDurationMinutes = maxDurationMinutes;
		this.reservedAmount = reservedAmount;
		this.billableSeconds = 0;
		this.requestedAt = requestedAt;
		this.expiresAt = expiresAt;
		this.livekitRoomName = livekitRoomName;
	}

	public String getSessionUid() {
		return sessionUid;
	}

	public void setSessionUid(String sessionUid) {
		this.sessionUid = sessionUid;
	}

	public Long getCourseId() {
		return courseId;
	}

	public void setCourseId(Long courseId) {
		this.courseId = courseId;
	}

	public Long getInstructorId() {
		return instructorId;
	}

	public void setInstructorId(Long instructorId) {
		this.instructorId = instructorId;
	}

	public Long getStudentId() {
		return studentId;
	}

	public void setStudentId(Long studentId) {
		this.studentId = studentId;
	}

	public DoubtSessionStatus getStatus() {
		return status;
	}

	public void setStatus(DoubtSessionStatus status) {
		this.status = status;
	}

	public int getHourlyRatePgc() {
		return hourlyRatePgc;
	}

	public void setHourlyRatePgc(int hourlyRatePgc) {
		this.hourlyRatePgc = hourlyRatePgc;
	}

	public int getMaxDurationMinutes() {
		return maxDurationMinutes;
	}

	public void setMaxDurationMinutes(int maxDurationMinutes) {
		this.maxDurationMinutes = maxDurationMinutes;
	}

	public BigDecimal getReservedAmount() {
		return reservedAmount;
	}

	public void setReservedAmount(BigDecimal reservedAmount) {
		this.reservedAmount = reservedAmount;
	}

	public BigDecimal getFinalCharge() {
		return finalCharge;
	}

	public void setFinalCharge(BigDecimal finalCharge) {
		this.finalCharge = finalCharge;
	}

	public BigDecimal getRefundedAmount() {
		return refundedAmount;
	}

	public void setRefundedAmount(BigDecimal refundedAmount) {
		this.refundedAmount = refundedAmount;
	}

	public int getBillableSeconds() {
		return billableSeconds;
	}

	public void setBillableSeconds(int billableSeconds) {
		this.billableSeconds = billableSeconds;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(Instant requestedAt) {
		this.requestedAt = requestedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public void setAcceptedAt(Instant acceptedAt) {
		this.acceptedAt = acceptedAt;
	}

	public Instant getConnectDeadlineAt() {
		return connectDeadlineAt;
	}

	public void setConnectDeadlineAt(Instant connectDeadlineAt) {
		this.connectDeadlineAt = connectDeadlineAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(Instant endedAt) {
		this.endedAt = endedAt;
	}

	public String getLivekitRoomName() {
		return livekitRoomName;
	}

	public void setLivekitRoomName(String livekitRoomName) {
		this.livekitRoomName = livekitRoomName;
	}

	public boolean isStudentConnected() {
		return studentConnected;
	}

	public void setStudentConnected(boolean studentConnected) {
		this.studentConnected = studentConnected;
	}

	public boolean isInstructorConnected() {
		return instructorConnected;
	}

	public void setInstructorConnected(boolean instructorConnected) {
		this.instructorConnected = instructorConnected;
	}

	public Instant getLastBothConnectedAt() {
		return lastBothConnectedAt;
	}

	public void setLastBothConnectedAt(Instant lastBothConnectedAt) {
		this.lastBothConnectedAt = lastBothConnectedAt;
	}

	public Instant getLastDisconnectedAt() {
		return lastDisconnectedAt;
	}

	public void setLastDisconnectedAt(Instant lastDisconnectedAt) {
		this.lastDisconnectedAt = lastDisconnectedAt;
	}

	public Instant getReconnectDeadlineAt() {
		return reconnectDeadlineAt;
	}

	public void setReconnectDeadlineAt(Instant reconnectDeadlineAt) {
		this.reconnectDeadlineAt = reconnectDeadlineAt;
	}

	public String getEndReason() {
		return endReason;
	}

	public void setEndReason(String endReason) {
		this.endReason = endReason;
	}
}

