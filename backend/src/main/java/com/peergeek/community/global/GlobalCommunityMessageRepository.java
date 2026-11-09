package com.peergeek.community.global;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlobalCommunityMessageRepository extends JpaRepository<GlobalCommunityMessageEntity, Long> {

	/**
	 * Find the latest active messages ordered by id DESC for initial load.
	 */
	@Query("SELECT m FROM GlobalCommunityMessageEntity m JOIN FETCH m.user WHERE m.deletedAt IS NULL ORDER BY m.id DESC")
	List<GlobalCommunityMessageEntity> findLatestActive(Pageable pageable);

	/**
	 * Find new active messages after a given message ID for polling (ordered ASC).
	 */
	@Query("SELECT m FROM GlobalCommunityMessageEntity m JOIN FETCH m.user WHERE m.deletedAt IS NULL AND m.id > :afterId ORDER BY m.id ASC")
	List<GlobalCommunityMessageEntity> findNewerActive(@Param("afterId") Long afterId, Pageable pageable);

	/**
	 * Find older active messages before a given message ID for backwards pagination (ordered DESC).
	 */
	@Query("SELECT m FROM GlobalCommunityMessageEntity m JOIN FETCH m.user WHERE m.deletedAt IS NULL AND m.id < :beforeId ORDER BY m.id DESC")
	List<GlobalCommunityMessageEntity> findOlderActive(@Param("beforeId") Long beforeId, Pageable pageable);

	/**
	 * Admin query: retrieve messages with optional filters (including soft-deleted).
	 */
	@Query("""
			SELECT m FROM GlobalCommunityMessageEntity m
			JOIN FETCH m.user
			LEFT JOIN FETCH m.deletedBy
			WHERE (:includeDeleted IS TRUE OR m.deletedAt IS NULL)
			  AND (:userId IS NULL OR m.user.id = :userId)
			  AND (:query IS NULL OR LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')))
			ORDER BY m.id DESC
			""")
	Page<GlobalCommunityMessageEntity> findForAdmin(
			@Param("includeDeleted") Boolean includeDeleted,
			@Param("userId") Long userId,
			@Param("query") String query,
			Pageable pageable);
}
