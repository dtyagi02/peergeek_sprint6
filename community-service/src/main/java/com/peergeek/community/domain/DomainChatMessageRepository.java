package com.peergeek.community.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainChatMessageRepository extends JpaRepository<DomainChatMessageEntity, Long> {

	@Query("SELECT m FROM DomainChatMessageEntity m WHERE m.domainId = :domainId ORDER BY m.id DESC")
	List<DomainChatMessageEntity> findRecentMessagesDesc(@Param("domainId") Long domainId, Pageable pageable);

	@Query("SELECT m FROM DomainChatMessageEntity m WHERE m.domainId = :domainId AND m.id < :beforeId ORDER BY m.id DESC")
	List<DomainChatMessageEntity> findMessagesBeforeDesc(
			@Param("domainId") Long domainId,
			@Param("beforeId") Long beforeId,
			Pageable pageable);

	@Query("SELECT m FROM DomainChatMessageEntity m WHERE m.domainId = :domainId AND m.id > :afterId ORDER BY m.id ASC")
	List<DomainChatMessageEntity> findMessagesAfterAsc(
			@Param("domainId") Long domainId,
			@Param("afterId") Long afterId);
}

