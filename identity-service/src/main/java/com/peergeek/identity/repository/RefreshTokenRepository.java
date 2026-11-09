package com.peergeek.identity.repository;

import java.util.List;
import java.util.Optional;
import com.peergeek.identity.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from RefreshTokenEntity token where token.tokenHash = :hash")
	Optional<RefreshTokenEntity> findByHashForUpdate(@Param("hash") String hash);

	List<RefreshTokenEntity> findAllByUser_IdAndFamilyId(Long userId, String familyId);

	@Modifying
	@Query("update RefreshTokenEntity token set token.revokedAt = current_instant() where token.user.id = :userId and token.revokedAt is null")
	int revokeAllByUserId(@Param("userId") Long userId);

	@Modifying
	@Query("update RefreshTokenEntity token set token.revokedAt = current_instant() where token.user.id = :userId and token.familyId = :familyId and token.revokedAt is null")
	int revokeFamily(@Param("userId") Long userId, @Param("familyId") String familyId);
}
