package com.peergeek.walletpayment.wallet.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BatchReferralRewardRequest(
        @NotNull Long referralId,
        @NotNull Long inviterUserId,
        @NotNull BigDecimal inviterRewardAmount,
        String inviterDescription,
        @NotNull Long newUserId,
        @NotNull BigDecimal newUserRewardAmount,
        String newUserDescription
) {}

