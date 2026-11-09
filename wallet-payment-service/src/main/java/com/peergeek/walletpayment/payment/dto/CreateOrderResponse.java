package com.peergeek.walletpayment.payment.dto;

import java.math.BigDecimal;

public record CreateOrderResponse(
        Long orderId,
        String razorpayOrderId,
        long amountPaise,
        BigDecimal inrAmount,
        String currency,
        BigDecimal pgcAmount,
        String keyId,
        String packageId,
        String userEmail,
        String userName
) {}

