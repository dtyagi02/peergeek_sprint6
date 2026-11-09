package com.peergeek.community.posts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityCommentEntity, Long> {

	List<CommunityCommentEntity> findByPostIdOrderByCreatedAtAsc(Long postId);

	long countByPostId(Long postId);
}

