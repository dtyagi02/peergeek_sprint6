package com.peergeek.courselearning.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePublicStatsRepository extends JpaRepository<CoursePublicStatsEntity, Long> {
}
