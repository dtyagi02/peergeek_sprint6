package com.peergeek.doubtsession.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.doubtsession.DoubtSessionEntity;
import com.peergeek.doubtsession.DoubtSessionStatus;

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
		return new DoubtSessionDto(
				entity.getId(),
				entity.getSessionUid(),
				entity.getCourse().getId(),
				entity.getCourse().getTitle(),
				entity.getInstructor().getId(),
				entity.getInstructor().getDisplayName(),
				entity.getStudent().getId(),
				entity.getStudent().getDisplayName(),
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
				entity.getEndReason());
	}
}
