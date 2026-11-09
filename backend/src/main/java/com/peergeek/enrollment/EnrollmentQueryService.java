package com.peergeek.enrollment;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EnrollmentQueryService {

	boolean isActivelyEnrolled(long userId, long courseId);

	Optional<Long> getActiveEnrollmentId(long userId, long courseId);

	Set<Long> findActiveEnrolledCourseIds(long userId, Collection<Long> courseIds);

	Map<Long, Long> getActiveEnrollmentIds(long userId, Collection<Long> courseIds);

	Page<Long> listActiveLearnerIds(long courseId, Pageable pageable);

	Page<Long> listActiveCourseIds(long userId, Pageable pageable);
}
