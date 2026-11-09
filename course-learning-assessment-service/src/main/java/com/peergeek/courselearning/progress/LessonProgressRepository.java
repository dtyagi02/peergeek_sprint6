package com.peergeek.courselearning.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgressEntity, Long> {

    Optional<LessonProgressEntity> findByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);

    List<LessonProgressEntity> findByEnrollmentId(Long enrollmentId);

    List<LessonProgressEntity> findByEnrollmentIdAndLessonIdIn(Long enrollmentId, List<Long> lessonIds);

    int countByEnrollmentId(Long enrollmentId);

    void deleteByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);

    void deleteByEnrollmentId(Long enrollmentId);
}
