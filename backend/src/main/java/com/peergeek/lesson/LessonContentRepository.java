package com.peergeek.lesson;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonContentRepository extends JpaRepository<LessonContentEntity, Long> {
	Optional<LessonContentEntity> findByLesson_Id(Long lessonId);

	@Modifying
	@Query("delete from LessonContentEntity c where c.lessonId in :lessonIds")
	void deleteAllByLessonIds(@Param("lessonIds") java.util.List<Long> lessonIds);
}
