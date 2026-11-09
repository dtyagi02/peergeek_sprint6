package com.peergeek.payment.pricing;

import java.math.BigDecimal;

public record RechargePackage(
		String id,
		String name,
		BigDecimal inrAmount,
		long amountPaise,
		String currency,
		BigDecimal pgcAmount,
		String badge,
		String description
) {
	public static RechargePackage of(
			String id,
			String name,
			long inrRupees,
			long pgcAmount,
			String badge,
			String description) {
		return new RechargePackage(
				id,
				name,
				BigDecimal.valueOf(inrRupees),
				inrRupees * 100L,
				"INR",
				BigDecimal.valueOf(pgcAmount),
				badge,
				description
		);
	}
}

