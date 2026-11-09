package com.peergeek.doubtsession.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.doubtsession.entity.DoubtSessionEntity;
import com.peergeek.doubtsession.entity.DoubtSessionStatus;

public record DoubtSessionDto(
		Long id,
		String sessionUid,
		Long courseId,
		String courseTitle,
		Long instructorId,
		String instructorDisplayName,
		Long studentId,
		String studentDisplayName,
		DoubtSessionStatus status,
		int hourlyRatePgc,
		int maxDurationMinutes,
		BigDecimal reservedAmount,
		BigDecimal finalCharge,
		BigDecimal refundedAmount,
		int billableSeconds,
		Instant requestedAt,
		Instant expiresAt,
		Instant acceptedAt,
		Instant connectDeadlineAt,
		Instant startedAt,
		Instant endedAt,
		String livekitRoomName,
		boolean studentConnected,
		boolean instructorConnected,
		Instant reconnectDeadlineAt,
		String endReason
) {
	public static DoubtSessionDto from(DoubtSessionEntity entity) {
		return from(entity, "Course #" + entity.getCourseId(), "Instructor #" + entity.getInstructorId(), "Student #" + entity.getStudentId());
	}

	public static DoubtSessionDto from(DoubtSessionEntity entity, String courseTitle, String instructorDisplayName, String studentDisplayName) {
		return new DoubtSessionDto(
				entity.getId(),
				entity.getSessionUid(),
				entity.getCourseId(),
				courseTitle,
				entity.getInstructorId(),
				instructorDisplayName,
				entity.getStudentId(),
				studentDisplayName,
				entity.getStatus(),
				entity.getHourlyRatePgc(),
				entity.getMaxDurationMinutes(),
				entity.getReservedAmount(),
				entity.getFinalCharge(),
				entity.getRefundedAmount(),
				entity.getBillableSeconds(),
				entity.getRequestedAt(),
				entity.getExpiresAt(),
				entity.getAcceptedAt(),
				entity.getConnectDeadlineAt(),
				entity.getStartedAt(),
				entity.getEndedAt(),
				entity.getLivekitRoomName(),
				entity.isStudentConnected(),
				entity.isInstructorConnected(),
				entity.getReconnectDeadlineAt(),
				entity.getEndReason()
		);
	}
}

