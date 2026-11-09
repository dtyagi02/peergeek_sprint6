package com.peergeek.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentRepository extends JpaRepository<CommunityCommentEntity, Long> {

	List<CommunityCommentEntity> findByPostIdOrderByCreatedAtAsc(Long postId);

	long countByPostId(Long postId);
}
