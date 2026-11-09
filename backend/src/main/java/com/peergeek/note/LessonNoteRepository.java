package com.peergeek.note;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonNoteRepository extends JpaRepository<LessonNoteEntity, Long> {

	Optional<LessonNoteEntity> findByUserIdAndLessonId(Long userId, Long lessonId);

	List<LessonNoteEntity> findByUserIdAndCourseId(Long userId, Long courseId);

	boolean existsByUserIdAndLessonId(Long userId, Long lessonId);

	void deleteByUserIdAndLessonId(Long userId, Long lessonId);
}
