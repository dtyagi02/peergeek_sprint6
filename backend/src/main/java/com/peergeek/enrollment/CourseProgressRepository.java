package com.peergeek.enrollment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseProgressRepository extends JpaRepository<CourseProgressEntity, Long> {

	List<CourseProgressEntity> findByEnrollmentIdIn(Collection<Long> enrollmentIds);

	@Modifying
	@Query("update CourseProgressEntity cp set cp.stale = true where cp.courseId = :courseId")
	int markStaleByCourseId(@Param("courseId") Long courseId);
}
