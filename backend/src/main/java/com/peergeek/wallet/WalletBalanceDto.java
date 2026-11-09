package com.peergeek.wallet;

import java.math.BigDecimal;

public record WalletBalanceDto(
		BigDecimal balance,
		String currency,
		String currencyCode
) {
	public WalletBalanceDto(BigDecimal balance, String currencyCode) {
		this(balance, currencyCode, currencyCode);
	}
}
