package com.peergeek.outbox.events;

import java.math.BigDecimal;

public record WalletCreditedEvent(
        Long userId,
        BigDecimal amount,
        String transactionType,
        String referenceId,
        String description
) {}

