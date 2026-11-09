package com.peergeek.courselearning.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseTopicRepository extends JpaRepository<CourseTopicEntity, Long> {
    List<CourseTopicEntity> findByCourseId(Long courseId);
    List<CourseTopicEntity> findByCourseIdOrderByPositionAsc(Long courseId);
    void deleteByCourseId(Long courseId);
}
