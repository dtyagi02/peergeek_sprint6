package com.peergeek.community.global;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GlobalCommunityMessageRepository extends JpaRepository<GlobalCommunityMessageEntity, Long> {

	@Query("SELECT m FROM GlobalCommunityMessageEntity m WHERE m.deletedAt IS NULL ORDER BY m.id DESC")
	List<GlobalCommunityMessageEntity> findLatestActive(Pageable pageable);

	@Query("SELECT m FROM GlobalCommunityMessageEntity m WHERE m.deletedAt IS NULL AND m.id > :afterId ORDER BY m.id ASC")
	List<GlobalCommunityMessageEntity> findNewerActive(@Param("afterId") Long afterId, Pageable pageable);

	@Query("SELECT m FROM GlobalCommunityMessageEntity m WHERE m.deletedAt IS NULL AND m.id < :beforeId ORDER BY m.id DESC")
	List<GlobalCommunityMessageEntity> findOlderActive(@Param("beforeId") Long beforeId, Pageable pageable);

	@Query("""
			SELECT m FROM GlobalCommunityMessageEntity m
			WHERE (:includeDeleted IS TRUE OR m.deletedAt IS NULL)
			  AND (:userId IS NULL OR m.userId = :userId)
			  AND (:query IS NULL OR LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')))
			ORDER BY m.id DESC
			""")
	Page<GlobalCommunityMessageEntity> findForAdmin(
			@Param("includeDeleted") Boolean includeDeleted,
			@Param("userId") Long userId,
			@Param("query") String query,
			Pageable pageable);
}

