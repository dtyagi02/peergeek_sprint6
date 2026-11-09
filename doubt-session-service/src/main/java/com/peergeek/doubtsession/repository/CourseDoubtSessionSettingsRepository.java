package com.peergeek.doubtsession.repository;

import java.util.List;
import java.util.Optional;
import com.peergeek.doubtsession.entity.CourseDoubtSessionSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseDoubtSessionSettingsRepository extends JpaRepository<CourseDoubtSessionSettingsEntity, Long> {

	Optional<CourseDoubtSessionSettingsEntity> findByCourseId(Long courseId);

	List<CourseDoubtSessionSettingsEntity> findByInstructorId(Long instructorId);
}

