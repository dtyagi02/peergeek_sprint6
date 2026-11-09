package com.peergeek.community.global;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalCommunityBlockRepository extends JpaRepository<GlobalCommunityBlockEntity, Long> {

	@Query("SELECT b FROM GlobalCommunityBlockEntity b WHERE b.userId = :userId AND b.unblockedAt IS NULL")
	Optional<GlobalCommunityBlockEntity> findActiveBlockByUserId(@Param("userId") Long userId);

	@Query("SELECT COUNT(b) > 0 FROM GlobalCommunityBlockEntity b WHERE b.userId = :userId AND b.unblockedAt IS NULL")
	boolean isUserBlocked(@Param("userId") Long userId);

	@Query("SELECT b FROM GlobalCommunityBlockEntity b WHERE b.unblockedAt IS NULL ORDER BY b.createdAt DESC")
	List<GlobalCommunityBlockEntity> findAllActiveBlocks();
}

