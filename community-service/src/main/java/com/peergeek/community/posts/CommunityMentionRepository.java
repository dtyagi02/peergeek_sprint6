package com.peergeek.community.posts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityMentionRepository extends JpaRepository<CommunityMentionEntity, Long> {

	boolean existsByPostIdAndCommentIdAndMentionedUserId(Long postId, Long commentId, Long mentionedUserId);
}

