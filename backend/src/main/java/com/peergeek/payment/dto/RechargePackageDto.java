package com.peergeek.payment.dto;

import java.math.BigDecimal;
import com.peergeek.payment.pricing.RechargePackage;

public record RechargePackageDto(
		String id,
		String name,
		BigDecimal inrAmount,
		long amountPaise,
		String currency,
		BigDecimal pgcAmount,
		String badge,
		String description
) {
	public static RechargePackageDto from(RechargePackage pkg) {
		return new RechargePackageDto(
				pkg.id(),
				pkg.name(),
				pkg.inrAmount(),
				pkg.amountPaise(),
				pkg.currency(),
				pkg.pgcAmount(),
				pkg.badge(),
				pkg.description()
		);
	}
}

