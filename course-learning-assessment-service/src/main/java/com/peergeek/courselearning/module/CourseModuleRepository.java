package com.peergeek.courselearning.module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseModuleRepository extends JpaRepository<CourseModuleEntity, Long> {
    List<CourseModuleEntity> findByCourseIdOrderByPositionAsc(Long courseId);
    void deleteByCourseId(Long courseId);
    int countByCourseId(Long courseId);
}

