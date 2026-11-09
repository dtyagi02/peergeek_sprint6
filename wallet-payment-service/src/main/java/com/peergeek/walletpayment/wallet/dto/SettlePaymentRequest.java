package com.peergeek.walletpayment.wallet.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SettlePaymentRequest(
        @NotNull Long studentUserId,
        @NotNull Long instructorUserId,
        @NotNull BigDecimal reservedAmount,
        @NotNull BigDecimal chargeAmount,
        String referenceType,
        String referenceId,
        String description
) {}

