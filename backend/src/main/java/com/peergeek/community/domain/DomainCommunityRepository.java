package com.peergeek.community.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainCommunityRepository extends JpaRepository<DomainCommunityEntity, Long> {

	Optional<DomainCommunityEntity> findByCategoryId(Long categoryId);

	@Query("SELECT dc FROM DomainCommunityEntity dc JOIN FETCH dc.category c WHERE c.slug = :slug")
	Optional<DomainCommunityEntity> findByCategorySlug(@Param("slug") String slug);

	@Query("SELECT dc FROM DomainCommunityEntity dc JOIN FETCH dc.category c WHERE c.active = true ORDER BY c.sortOrder ASC")
	List<DomainCommunityEntity> findAllActive();
}
