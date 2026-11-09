package com.peergeek.courselearning.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LearningUserProfileRepository extends JpaRepository<LearningUserProfileEntity, Long> {
    Optional<LearningUserProfileEntity> findByUsername(String username);
}

