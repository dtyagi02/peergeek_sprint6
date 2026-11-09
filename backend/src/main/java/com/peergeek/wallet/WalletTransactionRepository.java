package com.peergeek.wallet;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, Long> {

	List<WalletTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

	Page<WalletTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	List<WalletTransactionEntity> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
