package com.peergeek.walletpayment.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentVerificationRequest(
        @NotBlank(message = "Razorpay order ID is required")
        String razorpayOrderId,

        @NotBlank(message = "Razorpay payment ID is required")
        String razorpayPaymentId,

        @NotBlank(message = "Razorpay signature is required")
        String razorpaySignature
) {}

