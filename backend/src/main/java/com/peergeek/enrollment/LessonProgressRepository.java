package com.peergeek.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgressEntity, Long> {

	Optional<LessonProgressEntity> findByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);

	List<LessonProgressEntity> findByEnrollmentId(Long enrollmentId);

	List<LessonProgressEntity> findByEnrollmentIdAndLessonIdIn(Long enrollmentId, Collection<Long> lessonIds);

	void deleteByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);
}
