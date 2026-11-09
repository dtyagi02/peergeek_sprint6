package com.peergeek.walletpayment.wallet.dto;

import com.peergeek.walletpayment.transaction.WalletTransactionEntity;
import com.peergeek.walletpayment.transaction.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionDto(
        Long id,
        Long userId,
        WalletTransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        BigDecimal reservedAfter,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt
) {
    public static WalletTransactionDto from(WalletTransactionEntity entity) {
        return new WalletTransactionDto(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getAmount(),
                entity.getBalanceAfter(),
                entity.getReservedAfter(),
                entity.getReferenceType(),
                entity.getReferenceId(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }
}

