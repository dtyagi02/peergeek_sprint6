package com.peergeek.community.profile;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityUserProfileRepository extends JpaRepository<CommunityUserProfileEntity, Long> {

    Optional<CommunityUserProfileEntity> findByUsername(String username);

    @Query("""
           SELECT u FROM CommunityUserProfileEntity u
           WHERE u.enabled = true
           AND (:prefix IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT(:prefix, '%'))
             OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :prefix, '%')))
           ORDER BY u.username ASC
           """)
    List<CommunityUserProfileEntity> findMentionSuggestions(@Param("prefix") String prefix, Pageable pageable);
}

