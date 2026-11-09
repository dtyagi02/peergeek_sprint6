package com.peergeek.community.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPostEntity, Long> {

	Page<CommunityPostEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

