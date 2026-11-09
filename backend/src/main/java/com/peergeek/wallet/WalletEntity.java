package com.peergeek.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.peergeek.common.BaseEntity;
import com.peergeek.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallets")
public class WalletEntity extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private UserEntity user;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

	@Column(name = "reserved_balance", nullable = false, precision = 19, scale = 2)
	private BigDecimal reservedBalance = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

	@Column(name = "currency_code", nullable = false, length = 10)
	private String currencyCode = "PGC";

	protected WalletEntity() {
	}

	public WalletEntity(UserEntity user) {
		this(user, BigDecimal.ZERO, "PGC");
	}

	public WalletEntity(UserEntity user, BigDecimal balance, String currencyCode) {
		this.user = user;
		this.balance = balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
		this.currencyCode = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim() : "PGC";
	}

	public UserEntity getUser() {
		return user;
	}

	public void setUser(UserEntity user) {
		this.user = user;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
	}

	public BigDecimal getReservedBalance() {
		return reservedBalance;
	}

	public void setReservedBalance(BigDecimal reservedBalance) {
		this.reservedBalance = reservedBalance != null ? reservedBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
	}

	public BigDecimal getAvailableBalance() {
		return balance.subtract(reservedBalance != null ? reservedBalance : BigDecimal.ZERO);
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim() : "PGC";
	}
}
