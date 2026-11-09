package com.peergeek.courselearning.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {

    List<ResourceEntity> findByLessonIdAndDeletedAtIsNullOrderByPositionAsc(Long lessonId);

    List<ResourceEntity> findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(Long courseId);

    Optional<ResourceEntity> findByIdAndDeletedAtIsNull(Long id);

    Optional<ResourceEntity> findByObjectKey(String objectKey);

    int countByLessonIdAndDeletedAtIsNull(Long lessonId);
}

