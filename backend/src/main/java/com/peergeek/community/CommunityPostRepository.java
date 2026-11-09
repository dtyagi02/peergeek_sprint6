package com.peergeek.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPostEntity, Long> {

	Page<CommunityPostEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
