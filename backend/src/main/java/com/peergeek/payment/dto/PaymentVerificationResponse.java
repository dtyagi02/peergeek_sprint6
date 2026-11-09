package com.peergeek.payment.dto;

import java.math.BigDecimal;
import com.peergeek.payment.PaymentOrderStatus;

public record PaymentVerificationResponse(
		boolean success,
		Long orderId,
		String razorpayOrderId,
		String razorpayPaymentId,
		PaymentOrderStatus status,
		BigDecimal pgcAmount,
		BigDecimal newBalance,
		String message
) {}

