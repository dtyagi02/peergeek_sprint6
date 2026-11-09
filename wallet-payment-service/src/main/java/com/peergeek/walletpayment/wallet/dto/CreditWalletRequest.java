package com.peergeek.walletpayment.wallet.dto;

import com.peergeek.walletpayment.transaction.WalletTransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreditWalletRequest(
        @NotNull Long userId,
        @NotNull @Positive BigDecimal amount,
        @NotNull WalletTransactionType type,
        String referenceType,
        String referenceId,
        String description
) {}

