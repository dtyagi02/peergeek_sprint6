package com.peergeek.courselearning.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonExpectedResourceRepository extends JpaRepository<LessonExpectedResourceEntity, Long> {
    List<LessonExpectedResourceEntity> findByLessonId(Long lessonId);
    void deleteByLessonId(Long lessonId);
}

