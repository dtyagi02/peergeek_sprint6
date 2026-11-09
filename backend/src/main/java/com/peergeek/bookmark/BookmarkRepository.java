package com.peergeek.bookmark;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {

	Optional<BookmarkEntity> findByUserIdAndTargetTypeAndCourseIdAndLessonIdIsNull(
			Long userId, BookmarkTargetType targetType, Long courseId);

	Optional<BookmarkEntity> findByUserIdAndTargetTypeAndCourseIdAndLessonId(
			Long userId, BookmarkTargetType targetType, Long courseId, Long lessonId);

	List<BookmarkEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

	List<BookmarkEntity> findByUserIdAndTargetTypeOrderByCreatedAtDesc(
			Long userId, BookmarkTargetType targetType);

	void deleteByUserIdAndTargetTypeAndCourseIdAndLessonIdIsNull(
			Long userId, BookmarkTargetType targetType, Long courseId);

	void deleteByUserIdAndTargetTypeAndCourseIdAndLessonId(
			Long userId, BookmarkTargetType targetType, Long courseId, Long lessonId);
}
