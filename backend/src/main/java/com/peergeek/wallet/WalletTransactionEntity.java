package com.peergeek.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransactionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wallet_id", nullable = false)
	private WalletEntity wallet;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WalletTransactionType type;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
	private BigDecimal balanceAfter;

	@Column(name = "reserved_after", nullable = false, precision = 19, scale = 2)
	private BigDecimal reservedAfter;

	@Column(name = "reference_type", length = 64)
	private String referenceType;

	@Column(name = "reference_id", length = 64)
	private String referenceId;

	@Column(length = 255)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WalletTransactionEntity() {
	}

	public WalletTransactionEntity(
			WalletEntity wallet,
			Long userId,
			WalletTransactionType type,
			BigDecimal amount,
			BigDecimal balanceAfter,
			BigDecimal reservedAfter,
			String referenceType,
			String referenceId,
			String description) {
		this.wallet = wallet;
		this.userId = userId;
		this.type = type;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.reservedAfter = reservedAfter;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.description = description;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public WalletEntity getWallet() {
		return wallet;
	}

	public Long getUserId() {
		return userId;
	}

	public WalletTransactionType getType() {
		return type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public BigDecimal getReservedAfter() {
		return reservedAfter;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
