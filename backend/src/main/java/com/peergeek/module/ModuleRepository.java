package com.peergeek.module;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleRepository extends JpaRepository<ModuleEntity, Long> {
	List<ModuleEntity> findAllByCourse_IdOrderByPositionAscIdAsc(Long courseId);
	long countByCourse_Id(Long courseId);
	Optional<ModuleEntity> findByIdAndCourse_DeletedAtIsNull(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ModuleEntity m where m.id = :id")
	Optional<ModuleEntity> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ModuleEntity m where m.id in :ids order by m.id")
	List<ModuleEntity> findAllByIdForUpdate(@Param("ids") List<Long> ids);

	@Query("select m.course.id from ModuleEntity m where m.id = :id and m.course.deletedAt is null")
	Optional<Long> findCourseIdById(@Param("id") Long id);
}
