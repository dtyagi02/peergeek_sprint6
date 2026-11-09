package com.peergeek.community.directmessage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectParticipantReadStateRepository extends JpaRepository<DirectParticipantReadStateEntity, DirectParticipantReadStateEntity.Id> {
	Optional<DirectParticipantReadStateEntity> findByIdConversationIdAndIdUserId(Long conversationId, Long userId);
}

