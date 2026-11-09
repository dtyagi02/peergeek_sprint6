package com.peergeek.payment;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_orders")
public class PaymentOrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "package_id", nullable = false, length = 64)
	private String packageId;

	@Column(name = "amount_paise", nullable = false)
	private Long amountPaise;

	@Column(name = "currency", nullable = false, length = 10)
	private String currency = "INR";

	@Column(name = "pgc_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal pgcAmount;

	@Column(name = "razorpay_order_id", nullable = false, unique = true, length = 128)
	private String razorpayOrderId;

	@Column(name = "razorpay_payment_id", unique = true, length = 128)
	private String razorpayPaymentId;

	@Column(name = "razorpay_signature", length = 255)
	private String razorpaySignature;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private PaymentOrderStatus status = PaymentOrderStatus.CREATED;

	@Column(name = "wallet_transaction_id")
	private Long walletTransactionId;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	@Column(name = "captured_at")
	private Instant capturedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PaymentOrderEntity() {
	}

	public PaymentOrderEntity(
			Long userId,
			String packageId,
			Long amountPaise,
			String currency,
			BigDecimal pgcAmount,
			String razorpayOrderId) {
		this.userId = userId;
		this.packageId = packageId;
		this.amountPaise = amountPaise;
		this.currency = (currency != null && !currency.isBlank()) ? currency.trim() : "INR";
		this.pgcAmount = pgcAmount;
		this.razorpayOrderId = razorpayOrderId;
		this.status = PaymentOrderStatus.CREATED;
		this.createdAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	@PrePersist
	protected void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
		this.updatedAt = Instant.now();
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getPackageId() {
		return packageId;
	}

	public Long getAmountPaise() {
		return amountPaise;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getPgcAmount() {
		return pgcAmount;
	}

	public String getRazorpayOrderId() {
		return razorpayOrderId;
	}

	public String getRazorpayPaymentId() {
		return razorpayPaymentId;
	}

	public void setRazorpayPaymentId(String razorpayPaymentId) {
		this.razorpayPaymentId = razorpayPaymentId;
	}

	public String getRazorpaySignature() {
		return razorpaySignature;
	}

	public void setRazorpaySignature(String razorpaySignature) {
		this.razorpaySignature = razorpaySignature;
	}

	public PaymentOrderStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentOrderStatus status) {
		this.status = status;
	}

	public Long getWalletTransactionId() {
		return walletTransactionId;
	}

	public void setWalletTransactionId(Long walletTransactionId) {
		this.walletTransactionId = walletTransactionId;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}

	public Instant getCapturedAt() {
		return capturedAt;
	}

	public void setCapturedAt(Instant capturedAt) {
		this.capturedAt = capturedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

