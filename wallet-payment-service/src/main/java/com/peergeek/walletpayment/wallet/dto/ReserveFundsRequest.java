package com.peergeek.walletpayment.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ReserveFundsRequest(
        @NotNull Long userId,
        @NotNull @Positive BigDecimal amount,
        String referenceType,
        String referenceId,
        String description
) {}

