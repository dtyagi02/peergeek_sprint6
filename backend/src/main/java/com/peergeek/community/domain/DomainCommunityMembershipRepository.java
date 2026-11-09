package com.peergeek.community.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainCommunityMembershipRepository extends JpaRepository<DomainCommunityMembershipEntity, Long> {

	boolean existsByDomainIdAndUserId(Long domainId, Long userId);

	Optional<DomainCommunityMembershipEntity> findByDomainIdAndUserId(Long domainId, Long userId);

	long countByDomainId(Long domainId);

	@Query("SELECT m FROM DomainCommunityMembershipEntity m WHERE m.domainId = :domainId ORDER BY m.joinedAt DESC")
	Page<DomainCommunityMembershipEntity> findMembersByDomainId(@Param("domainId") Long domainId, Pageable pageable);

	@Modifying
	@Query("DELETE FROM DomainCommunityMembershipEntity m WHERE m.domainId = :domainId AND m.userId = :userId")
	void deleteByDomainIdAndUserId(@Param("domainId") Long domainId, @Param("userId") Long userId);
}
