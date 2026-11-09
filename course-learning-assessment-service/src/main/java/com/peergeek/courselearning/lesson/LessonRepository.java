package com.peergeek.courselearning.lesson;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<LessonEntity, Long> {

    List<LessonEntity> findByModuleIdOrderByPositionAsc(Long moduleId);

    @Query("SELECT l FROM LessonEntity l JOIN CourseModuleEntity m ON l.moduleId = m.id WHERE m.courseId = :courseId ORDER BY m.position ASC, l.position ASC")
    List<LessonEntity> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(l) FROM LessonEntity l JOIN CourseModuleEntity m ON l.moduleId = m.id WHERE m.courseId = :courseId")
    int countByCourseId(@Param("courseId") Long courseId);

    int countByModuleId(Long moduleId);
}

