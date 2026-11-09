package com.peergeek.file;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonExpectedResourceRepository extends JpaRepository<LessonExpectedResourceEntity, Long> {
	List<LessonExpectedResourceEntity> findByLessonIdOrderByResourceTypeAsc(Long lessonId);
	List<LessonExpectedResourceEntity> findByLessonIdIn(List<Long> lessonIds);
	void deleteByLessonId(Long lessonId);
}
