package com.peergeek.file;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {
	List<ResourceEntity> findByLessonIdAndDeletedAtIsNullAndPurposeOrderByPositionAsc(
			Long lessonId, ResourcePurpose purpose);

	List<ResourceEntity> findByCourseIdAndPurposeAndDeletedAtIsNullOrderByCreatedAtDesc(
			Long courseId, ResourcePurpose purpose);

	List<ResourceEntity> findByCourseIdAndLessonIdIsNotNullAndDeletedAtIsNullOrderByLessonIdAscPositionAsc(
			Long courseId);

	Optional<ResourceEntity> findByIdAndDeletedAtIsNull(Long id);

	List<ResourceEntity> findByObjectKeyIn(Collection<String> objectKeys);

	List<ResourceEntity> findByStatusAndDeletedAtIsNullAndUpdatedAtBefore(
			ResourceStatus status, Instant updatedAt);

	List<ResourceEntity> findByDeletedAtIsNotNullAndDeletedAtBefore(Instant deletedAt);

	long countByLessonIdAndDeletedAtIsNullAndPurpose(Long lessonId, ResourcePurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ResourceEntity r where r.lessonId = :lessonId "
			+ "and r.purpose = :purpose and r.deletedAt is null order by r.position")
	List<ResourceEntity> lockByLessonAndPurpose(
			@Param("lessonId") Long lessonId, @Param("purpose") ResourcePurpose purpose);
}
