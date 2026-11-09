package com.peergeek.walletpayment.payment.dto;

import com.peergeek.walletpayment.payment.PaymentOrderStatus;
import java.math.BigDecimal;

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

