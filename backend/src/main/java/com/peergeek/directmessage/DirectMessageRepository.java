package com.peergeek.directmessage;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface DirectMessageRepository extends JpaRepository<DirectMessageEntity,Long> {
	Page<DirectMessageEntity> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);
	Optional<DirectMessageEntity> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);
	long countByConversationIdAndIdGreaterThan(Long conversationId, Long id);
	long countByConversationIdAndIdGreaterThanAndSenderIdNot(Long conversationId, Long id, Long senderId);
}
