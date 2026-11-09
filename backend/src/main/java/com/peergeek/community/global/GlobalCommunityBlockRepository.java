package com.peergeek.community.global;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlobalCommunityBlockRepository extends JpaRepository<GlobalCommunityBlockEntity, Long> {

	/**
	 * Find active block for a specific user (where unblocked_at is null).
	 */
	@Query("SELECT b FROM GlobalCommunityBlockEntity b JOIN FETCH b.user JOIN FETCH b.blockedBy WHERE b.user.id = :userId AND b.unblockedAt IS NULL")
	Optional<GlobalCommunityBlockEntity> findActiveBlockByUserId(@Param("userId") Long userId);

	/**
	 * Check if a user is actively blocked from posting.
	 */
	@Query("SELECT COUNT(b) > 0 FROM GlobalCommunityBlockEntity b WHERE b.user.id = :userId AND b.unblockedAt IS NULL")
	boolean isUserBlocked(@Param("userId") Long userId);

	/**
	 * Find all currently active blocks.
	 */
	@Query("SELECT b FROM GlobalCommunityBlockEntity b JOIN FETCH b.user JOIN FETCH b.blockedBy WHERE b.unblockedAt IS NULL ORDER BY b.createdAt DESC")
	List<GlobalCommunityBlockEntity> findAllActiveBlocks();
}
