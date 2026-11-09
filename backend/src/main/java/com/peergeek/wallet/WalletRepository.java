package com.peergeek.wallet;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

	Optional<WalletEntity> findByUserId(Long userId);

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select w from WalletEntity w where w.user.id = :userId")
	Optional<WalletEntity> findWithLockByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

	boolean existsByUserId(Long userId);
}
