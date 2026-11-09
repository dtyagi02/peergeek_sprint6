package com.peergeek.community.directmessage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectConversationRepository extends JpaRepository<DirectConversationEntity, Long> {

	@Query("SELECT c FROM DirectConversationEntity c WHERE c.userOneId = :a AND c.userTwoId = :b")
	Optional<DirectConversationEntity> findBetween(@Param("a") Long a, @Param("b") Long b);

	@Query("SELECT c FROM DirectConversationEntity c WHERE c.userOneId = :userId OR c.userTwoId = :userId ORDER BY c.updatedAt DESC")
	List<DirectConversationEntity> findForUser(@Param("userId") Long userId);
}

