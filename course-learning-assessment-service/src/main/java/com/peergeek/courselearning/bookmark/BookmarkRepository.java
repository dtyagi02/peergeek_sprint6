package com.peergeek.courselearning.bookmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {

    List<BookmarkEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BookmarkEntity> findByUserIdAndCourseIdAndLessonIdIsNull(Long userId, Long courseId);

    Optional<BookmarkEntity> findByUserIdAndCourseIdAndLessonId(Long userId, Long courseId, Long lessonId);

    boolean existsByUserIdAndCourseIdAndLessonIdIsNull(Long userId, Long courseId);

    boolean existsByUserIdAndCourseIdAndLessonId(Long userId, Long courseId, Long lessonId);

    void deleteByUserIdAndCourseIdAndLessonIdIsNull(Long userId, Long courseId);

    void deleteByUserIdAndCourseIdAndLessonId(Long userId, Long courseId, Long lessonId);
}

