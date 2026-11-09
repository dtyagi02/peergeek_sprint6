package com.peergeek.community.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomainCommunityRepository extends JpaRepository<DomainCommunityEntity, Long> {

	Optional<DomainCommunityEntity> findBySlug(String slug);

	Optional<DomainCommunityEntity> findByCategoryId(Long categoryId);

	List<DomainCommunityEntity> findAllByOrderByCreatedAtAsc();
}

