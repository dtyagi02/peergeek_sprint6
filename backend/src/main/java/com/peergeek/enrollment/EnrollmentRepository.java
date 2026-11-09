package com.peergeek.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

	Optional<EnrollmentEntity> findByUserIdAndCourseId(Long userId, Long courseId);

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
	@Query("select e from EnrollmentEntity e where e.userId = :userId and e.courseId = :courseId")
	Optional<EnrollmentEntity> findWithLockByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

	Optional<EnrollmentEntity> findByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);

	@Query("select e from EnrollmentEntity e join CourseProgressEntity cp on cp.enrollmentId = e.id "
			+ "where e.userId = :userId and e.status = com.peergeek.enrollment.EnrollmentStatus.ACTIVE "
			+ "and cp.percent >= 100")
	List<EnrollmentEntity> findCompletedEnrollments(@Param("userId") Long userId);

	boolean existsByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);

	int countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

	@Query("select count(cp) from CourseProgressEntity cp join EnrollmentEntity e on cp.enrollmentId = e.id "
			+ "where e.courseId = :courseId and e.status = com.peergeek.enrollment.EnrollmentStatus.ACTIVE and cp.completedAt is not null")
	int countCompletedByCourseId(@Param("courseId") Long courseId);

	Page<EnrollmentEntity> findByUserIdAndStatus(Long userId, EnrollmentStatus status, Pageable pageable);

	List<EnrollmentEntity> findByUserIdAndStatus(Long userId, EnrollmentStatus status);

	List<EnrollmentEntity> findByUserIdAndStatusAndCourseIdIn(Long userId, EnrollmentStatus status, Collection<Long> courseIds);

	@Query("select e.userId from EnrollmentEntity e where e.courseId = :courseId and e.status = com.peergeek.enrollment.EnrollmentStatus.ACTIVE order by e.id asc")
	Page<Long> findActiveLearnerIdsByCourseId(@Param("courseId") Long courseId, Pageable pageable);

	@Query("select e.courseId from EnrollmentEntity e where e.userId = :userId and e.status = com.peergeek.enrollment.EnrollmentStatus.ACTIVE order by e.id asc")
	Page<Long> findActiveCourseIdsByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("select e.courseId, count(e) from EnrollmentEntity e where e.courseId in :courseIds and e.status = com.peergeek.enrollment.EnrollmentStatus.ACTIVE group by e.courseId")
	List<Object[]> countActiveByCourseIds(@Param("courseIds") Collection<Long> courseIds);

	@Query("select count(distinct e.userId) from EnrollmentEntity e where e.courseId in :courseIds and e.status = com.peergeek.enrollment.EnrollmentStatus.ACTIVE")
	long countDistinctActiveLearnersByCourseIds(@Param("courseIds") Collection<Long> courseIds);
}
