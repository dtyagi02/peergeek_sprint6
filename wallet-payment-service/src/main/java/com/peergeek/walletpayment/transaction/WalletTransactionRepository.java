package com.peergeek.walletpayment.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, Long> {

    Page<WalletTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<WalletTransactionEntity> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    boolean existsByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    boolean existsByUserIdAndReferenceTypeAndReferenceId(Long userId, String referenceType, String referenceId);

    boolean existsByUserIdAndTypeAndReferenceTypeAndReferenceId(
            Long userId,
            WalletTransactionType type,
            String referenceType,
            String referenceId);
}

