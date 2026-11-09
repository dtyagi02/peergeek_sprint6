package com.peergeek.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.wallet.WalletTransactionEntity;
import com.peergeek.wallet.WalletTransactionType;

public record WalletTransactionDto(
		Long id,
		WalletTransactionType type,
		String typeLabel,
		BigDecimal amount,
		BigDecimal balanceAfter,
		BigDecimal reservedAfter,
		String referenceType,
		String referenceId,
		String description,
		Instant createdAt,
		BigDecimal inrAmount
) {
	public static WalletTransactionDto from(WalletTransactionEntity entity) {
		String label = formatLabel(entity.getType());
		BigDecimal inr = null;
		if (entity.getType() == WalletTransactionType.RAZORPAY_TOPUP) {
			inr = entity.getAmount(); // 1 PGC = 1 INR
		}
		return new WalletTransactionDto(
				entity.getId(),
				entity.getType(),
				label,
				entity.getAmount(),
				entity.getBalanceAfter(),
				entity.getReservedAfter(),
				entity.getReferenceType(),
				entity.getReferenceId(),
				entity.getDescription(),
				entity.getCreatedAt(),
				inr
		);
	}

	private static String formatLabel(WalletTransactionType type) {
		if (type == null) return "Transaction";
		return switch (type) {
			case RAZORPAY_TOPUP -> "Recharge";
			case REFERRAL_INVITER_REWARD -> "Referral Reward";
			case REFERRAL_NEW_USER_REWARD -> "Welcome Bonus";
			case CREDIT -> "Credit";
			case DEBIT -> "Charge";
			case RESERVE -> "Reserved";
			case RELEASE -> "Released";
		};
	}
}

