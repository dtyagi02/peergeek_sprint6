package com.peergeek.directmessage;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DirectParticipantReadStateRepository extends JpaRepository<DirectParticipantReadStateEntity,DirectParticipantReadStateEntity.Id> {
	Optional<DirectParticipantReadStateEntity> findByConversationIdAndUserId(Long conversationId, Long userId);
}
