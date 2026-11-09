package com.peergeek.community.directmessage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessageEntity, Long> {

	Optional<DirectMessageEntity> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

	@Query("SELECT COUNT(m) FROM DirectMessageEntity m WHERE m.conversationId = :conversationId AND m.id > :messageId AND m.senderId <> :senderId")
	long countByConversationIdAndIdGreaterThanAndSenderIdNot(
			@Param("conversationId") Long conversationId,
			@Param("messageId") Long messageId,
			@Param("senderId") Long senderId);

	Page<DirectMessageEntity> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);
}

