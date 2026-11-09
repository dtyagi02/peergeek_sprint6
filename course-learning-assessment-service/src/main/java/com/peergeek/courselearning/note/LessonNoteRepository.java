package com.peergeek.courselearning.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LessonNoteRepository extends JpaRepository<LessonNoteEntity, Long> {
    Optional<LessonNoteEntity> findByUserIdAndLessonId(Long userId, Long lessonId);
    void deleteByUserIdAndLessonId(Long userId, Long lessonId);
}

