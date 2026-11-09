package com.peergeek.lesson;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<LessonEntity, Long> {
	List<LessonEntity> findAllByModule_IdOrderByPositionAscIdAsc(Long moduleId);
	long countByModule_Id(Long moduleId);
	long countByModule_Course_Id(Long courseId);
	Optional<LessonEntity> findByIdAndModule_Course_DeletedAtIsNull(Long id);
	void deleteAllByModule_Id(Long moduleId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LessonEntity l where l.id = :id")
	Optional<LessonEntity> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select new com.peergeek.lesson.LessonOutlineRow(
				l.id, l.module.id, l.title, l.summary, l.position,
				case when c.lessonId is null then false else true end,
				coalesce(c.wordCount, 0))
			from LessonEntity l left join LessonContentEntity c on c.lesson = l
			where l.module.id in :moduleIds
			order by l.module.position, l.position, l.id
			""")
	List<LessonOutlineRow> findOutlineRowsByModuleIds(@Param("moduleIds") List<Long> moduleIds);
}
