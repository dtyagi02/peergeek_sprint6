package com.peergeek.walletpayment.payment;

import com.peergeek.walletpayment.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final PaymentService paymentService;

    public RazorpayWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Rejected Razorpay webhook request: missing X-Razorpay-Signature header");
            throw new BadRequestException("Missing X-Razorpay-Signature header");
        }

        paymentService.processWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

