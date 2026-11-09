package com.peergeek.outbox.events;

import java.math.BigDecimal;

public record PaymentCapturedEvent(
        Long userId,
        Long paymentOrderId,
        String razorpayOrderId,
        BigDecimal pgcAmount
) {}

