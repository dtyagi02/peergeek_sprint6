package com.peergeek.community.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomainCommunityMembershipRepository extends JpaRepository<DomainCommunityMembershipEntity, Long> {

	boolean existsByDomainIdAndUserId(Long domainId, Long userId);

	long countByDomainId(Long domainId);

	Page<DomainCommunityMembershipEntity> findByDomainIdOrderByJoinedAtDesc(Long domainId, Pageable pageable);

	Optional<DomainCommunityMembershipEntity> findByDomainIdAndUserId(Long domainId, Long userId);

	void deleteByDomainIdAndUserId(Long domainId, Long userId);
}

