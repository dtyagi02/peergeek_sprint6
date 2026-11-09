package com.peergeek.enrollment;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultEnrollmentQueryService implements EnrollmentQueryService {

	private final EnrollmentRepository enrollments;

	public DefaultEnrollmentQueryService(EnrollmentRepository enrollments) {
		this.enrollments = enrollments;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isActivelyEnrolled(long userId, long courseId) {
		return enrollments.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Long> getActiveEnrollmentId(long userId, long courseId) {
		return enrollments.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
				.map(EnrollmentEntity::getId);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Long> findActiveEnrolledCourseIds(long userId, Collection<Long> courseIds) {
		if (courseIds == null || courseIds.isEmpty()) {
			return Set.of();
		}
		return enrollments.findByUserIdAndStatusAndCourseIdIn(userId, EnrollmentStatus.ACTIVE, courseIds).stream()
				.map(EnrollmentEntity::getCourseId)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Long> getActiveEnrollmentIds(long userId, Collection<Long> courseIds) {
		if (courseIds == null || courseIds.isEmpty()) {
			return Map.of();
		}
		return enrollments.findByUserIdAndStatusAndCourseIdIn(userId, EnrollmentStatus.ACTIVE, courseIds).stream()
				.collect(Collectors.toUnmodifiableMap(
						EnrollmentEntity::getCourseId,
						EnrollmentEntity::getId,
						(existing, replacement) -> existing));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Long> listActiveLearnerIds(long courseId, Pageable pageable) {
		return enrollments.findActiveLearnerIdsByCourseId(courseId, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Long> listActiveCourseIds(long userId, Pageable pageable) {
		return enrollments.findActiveCourseIdsByUserId(userId, pageable);
	}
}
