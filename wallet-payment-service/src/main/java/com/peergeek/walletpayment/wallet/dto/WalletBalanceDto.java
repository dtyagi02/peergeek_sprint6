package com.peergeek.walletpayment.wallet.dto;

import java.math.BigDecimal;

public record WalletBalanceDto(
        BigDecimal balance,
        String currencyCode
) {
    public String currency() {
        return currencyCode;
    }
}

