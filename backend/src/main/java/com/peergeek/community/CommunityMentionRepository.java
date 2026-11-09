package com.peergeek.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityMentionRepository extends JpaRepository<CommunityMentionEntity, Long> {

	boolean existsByPostIdAndCommentIdAndMentionedUserId(Long postId, Long commentId, Long mentionedUserId);

	List<CommunityMentionEntity> findByPostId(Long postId);

	List<CommunityMentionEntity> findByMentionedUserId(Long mentionedUserId);
}
