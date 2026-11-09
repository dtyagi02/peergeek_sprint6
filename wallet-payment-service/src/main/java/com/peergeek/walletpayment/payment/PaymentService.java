package com.peergeek.walletpayment.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.walletpayment.exception.BadRequestException;
import com.peergeek.walletpayment.exception.ConflictException;
import com.peergeek.walletpayment.exception.ForbiddenException;
import com.peergeek.walletpayment.exception.NotFoundException;
import com.peergeek.walletpayment.outbox.OutboxEventWriter;
import com.peergeek.walletpayment.payment.dto.CreateOrderResponse;
import com.peergeek.walletpayment.payment.dto.PaymentVerificationRequest;
import com.peergeek.walletpayment.payment.dto.PaymentVerificationResponse;
import com.peergeek.walletpayment.payment.pricing.RechargePackage;
import com.peergeek.walletpayment.payment.pricing.RechargePricingService;
import com.peergeek.walletpayment.transaction.WalletTransactionType;
import com.peergeek.walletpayment.wallet.WalletService;
import com.peergeek.walletpayment.wallet.dto.WalletBalanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentOrderRepository paymentOrderRepository;
    private final RechargePricingService pricingService;
    private final RazorpayService razorpayService;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final RazorpaySettings settings;
    private final WalletService walletService;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            RechargePricingService pricingService,
            RazorpayService razorpayService,
            RazorpaySignatureVerifier signatureVerifier,
            RazorpaySettings settings,
            WalletService walletService,
            OutboxEventWriter outboxEventWriter,
            ObjectMapper objectMapper) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.pricingService = pricingService;
        this.razorpayService = razorpayService;
        this.signatureVerifier = signatureVerifier;
        this.settings = settings;
        this.walletService = walletService;
        this.outboxEventWriter = outboxEventWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateOrderResponse createTopupOrder(Long userId, String packageId, String userEmail, String userName) {
        RechargePackage pkg = pricingService.getPackage(packageId)
                .orElseThrow(() -> new BadRequestException("Invalid recharge package: " + packageId));

        String receipt = "rcpt_" + userId + "_" + System.currentTimeMillis();
        Map<String, String> notes = Map.of(
                "userId", String.valueOf(userId),
                "packageId", pkg.id(),
                "pgcAmount", pkg.pgcAmount().toPlainString()
        );

        RazorpayService.RazorpayOrder rzpOrder = razorpayService.createOrder(
                pkg.amountPaise(),
                pkg.currency(),
                receipt,
                notes
        );

        PaymentOrderEntity entity = new PaymentOrderEntity(
                userId,
                pkg.id(),
                pkg.amountPaise(),
                pkg.currency(),
                pkg.pgcAmount(),
                rzpOrder.id()
        );
        entity = paymentOrderRepository.save(entity);

        log.info("Created top-up payment order id={}, rzpOrderId={}, userId={}, packageId={}, pgcAmount={}",
                entity.getId(), rzpOrder.id(), userId, pkg.id(), pkg.pgcAmount());

        return new CreateOrderResponse(
                entity.getId(),
                rzpOrder.id(),
                pkg.amountPaise(),
                pkg.inrAmount(),
                pkg.currency(),
                pkg.pgcAmount(),
                settings.getKeyId(),
                pkg.id(),
                userEmail != null ? userEmail : "student@peergeek.local",
                userName != null ? userName : "Learner"
        );
    }

    @Transactional
    public PaymentVerificationResponse verifyTopupPayment(Long userId, PaymentVerificationRequest request) {
        PaymentOrderEntity order = paymentOrderRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .orElseThrow(() -> new NotFoundException("Payment order not found for orderId=" + request.razorpayOrderId()));

        if (!order.getUserId().equals(userId)) {
            log.warn("Unauthorized attempt to verify payment orderId={} by userId={}", order.getId(), userId);
            throw new ForbiddenException("You do not own this payment order");
        }

        if (order.getStatus() == PaymentOrderStatus.CAPTURED) {
            log.info("Payment order {} already captured, returning idempotent response", order.getId());
            WalletBalanceDto balance = walletService.getBalance(userId);
            return new PaymentVerificationResponse(
                    true,
                    order.getId(),
                    order.getRazorpayOrderId(),
                    order.getRazorpayPaymentId(),
                    order.getStatus(),
                    order.getPgcAmount(),
                    balance.balance(),
                    "Recharge already completed successfully"
            );
        }

        // 1. Cryptographic HMAC-SHA256 signature verification
        boolean validSignature = signatureVerifier.verifyPaymentSignature(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature(),
                settings.getKeySecret()
        );

        if (!validSignature) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order.setFailureReason("Invalid payment signature");
            order.setRazorpayPaymentId(request.razorpayPaymentId());
            order.setRazorpaySignature(request.razorpaySignature());
            paymentOrderRepository.save(order);
            log.warn("Invalid signature for payment orderId={}, rzpOrderId={}", order.getId(), order.getRazorpayOrderId());
            throw new BadRequestException("Invalid payment signature verification failed");
        }

        // 2. Fetch payment details from Razorpay to verify captured status & amounts
        RazorpayService.RazorpayPayment payment = razorpayService.fetchPayment(request.razorpayPaymentId());

        if (!payment.orderId().equals(order.getRazorpayOrderId())) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order.setFailureReason("Payment order mismatch");
            paymentOrderRepository.save(order);
            throw new BadRequestException("Payment order ID does not match gateway order");
        }

        if (payment.amount() != order.getAmountPaise() || !payment.currency().equalsIgnoreCase(order.getCurrency())) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order.setFailureReason("Payment amount or currency mismatch");
            paymentOrderRepository.save(order);
            throw new BadRequestException("Payment amount or currency mismatch");
        }

        if (!payment.captured() && !"captured".equalsIgnoreCase(payment.status())) {
            log.warn("Payment {} not captured yet (status={})", payment.id(), payment.status());
            throw new BadRequestException("Payment is not yet captured by payment gateway");
        }

        // 3. Atomically credit wallet and mark captured
        return processSuccessfulPayment(order.getId(), request.razorpayPaymentId(), request.razorpaySignature());
    }

    @Transactional
    public PaymentVerificationResponse processSuccessfulPayment(Long orderId, String paymentId, String signature) {
        PaymentOrderEntity order = paymentOrderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new NotFoundException("Payment order not found: " + orderId));

        if (order.getStatus() == PaymentOrderStatus.CAPTURED) {
            WalletBalanceDto balance = walletService.getBalance(order.getUserId());
            return new PaymentVerificationResponse(
                    true,
                    order.getId(),
                    order.getRazorpayOrderId(),
                    order.getRazorpayPaymentId(),
                    order.getStatus(),
                    order.getPgcAmount(),
                    balance.balance(),
                    "Recharge already credited"
            );
        }

        Optional<PaymentOrderEntity> existingPayment = paymentOrderRepository.findByRazorpayPaymentId(paymentId);
        if (existingPayment.isPresent() && !existingPayment.get().getId().equals(order.getId())) {
            log.error("Duplicate paymentId={} used for orderId={}, already assigned to orderId={}",
                    paymentId, order.getId(), existingPayment.get().getId());
            throw new ConflictException("Razorpay payment ID has already been credited to another order");
        }

        // Credit user's wallet
        String description = "GeekCoins Recharge: " + order.getPgcAmount().stripTrailingZeros().toPlainString() +
                " PGC (INR " + (order.getAmountPaise() / 100) + ")";

        walletService.credit(
                order.getUserId(),
                order.getPgcAmount(),
                WalletTransactionType.RAZORPAY_TOPUP,
                "RAZORPAY_PAYMENT",
                paymentId,
                description
        );

        order.setRazorpayPaymentId(paymentId);
        if (signature != null && !signature.isBlank()) {
            order.setRazorpaySignature(signature);
        }
        order.setStatus(PaymentOrderStatus.CAPTURED);
        order.setCapturedAt(Instant.now());
        order = paymentOrderRepository.saveAndFlush(order);

        WalletBalanceDto updatedBalance = walletService.getBalance(order.getUserId());

        // Publish PaymentCaptured Kafka outbox event
        outboxEventWriter.writeEvent(
                "peergeek.wallet.payment-captured.v1",
                "PaymentCaptured",
                Map.of(
                        "userId", order.getUserId(),
                        "pgcAmount", order.getPgcAmount().toPlainString(),
                        "razorpayOrderId", order.getRazorpayOrderId(),
                        "razorpayPaymentId", paymentId,
                        "amountPaise", order.getAmountPaise(),
                        "currency", order.getCurrency(),
                        "occurredAt", Instant.now().toString()
                )
        );

        log.info("Successfully processed recharge orderId={}, rzpPaymentId={}, userId={}, credited={} PGC, newBalance={}",
                order.getId(), paymentId, order.getUserId(), order.getPgcAmount(), updatedBalance.balance());

        return new PaymentVerificationResponse(
                true,
                order.getId(),
                order.getRazorpayOrderId(),
                paymentId,
                PaymentOrderStatus.CAPTURED,
                order.getPgcAmount(),
                updatedBalance.balance(),
                "Recharge successful! " + order.getPgcAmount().stripTrailingZeros().toPlainString() + " GeekCoins credited to your wallet."
        );
    }

    @Transactional
    public void processWebhook(String rawPayload, String signatureHeader) {
        if (!settings.isWebhookConfigured()) {
            log.warn("Razorpay webhook received but RAZORPAY_WEBHOOK_SECRET is not configured. Rejecting webhook.");
            throw new BadRequestException("Webhook processing is unavailable: webhook secret not configured");
        }

        boolean valid = signatureVerifier.verifyWebhookSignature(rawPayload, signatureHeader, settings.getWebhookSecret());
        if (!valid) {
            log.warn("Invalid Razorpay webhook signature");
            throw new BadRequestException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText();
            log.info("Processing verified Razorpay webhook event: {}", event);

            if ("payment.captured".equals(event) || "order.paid".equals(event)) {
                handlePaymentCapturedWebhook(root);
            } else if ("payment.failed".equals(event)) {
                handlePaymentFailedWebhook(root);
            } else {
                log.debug("Ignored Razorpay webhook event: {}", event);
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse or process Razorpay webhook payload: {}", ex.getMessage());
            throw new BadRequestException("Failed to process webhook payload");
        }
    }

    private void handlePaymentCapturedWebhook(JsonNode root) {
        JsonNode payload = root.path("payload");
        JsonNode paymentNode = payload.path("payment").path("entity");
        String rzpPaymentId = paymentNode.path("id").asText();
        String rzpOrderId = paymentNode.path("order_id").asText();

        if (rzpOrderId == null || rzpOrderId.isBlank()) {
            JsonNode orderNode = payload.path("order").path("entity");
            rzpOrderId = orderNode.path("id").asText();
        }

        if (rzpOrderId == null || rzpOrderId.isBlank()) {
            log.warn("Webhook payload missing order_id for paymentId={}", rzpPaymentId);
            return;
        }

        Optional<PaymentOrderEntity> orderOpt = paymentOrderRepository.findByRazorpayOrderId(rzpOrderId);
        if (orderOpt.isEmpty()) {
            log.warn("Webhook received for unknown Razorpay order_id={}", rzpOrderId);
            return;
        }

        PaymentOrderEntity order = orderOpt.get();
        processSuccessfulPayment(order.getId(), rzpPaymentId, null);
    }

    private void handlePaymentFailedWebhook(JsonNode root) {
        JsonNode payload = root.path("payload");
        JsonNode paymentNode = payload.path("payment").path("entity");
        String rzpPaymentId = paymentNode.path("id").asText();
        String rzpOrderId = paymentNode.path("order_id").asText();
        String errorDescription = paymentNode.path("error_description").asText();

        if (rzpOrderId != null && !rzpOrderId.isBlank()) {
            paymentOrderRepository.findByRazorpayOrderId(rzpOrderId).ifPresent(order -> {
                if (order.getStatus() == PaymentOrderStatus.CREATED) {
                    order.setStatus(PaymentOrderStatus.FAILED);
                    order.setFailureReason(errorDescription != null ? errorDescription : "Payment failed");
                    order.setRazorpayPaymentId(rzpPaymentId);
                    paymentOrderRepository.save(order);

                    outboxEventWriter.writeEvent(
                            "peergeek.wallet.payment-failed.v1",
                            "PaymentFailed",
                            Map.of(
                                    "userId", order.getUserId(),
                                    "razorpayOrderId", rzpOrderId,
                                    "razorpayPaymentId", rzpPaymentId != null ? rzpPaymentId : "",
                                    "failureReason", order.getFailureReason(),
                                    "occurredAt", Instant.now().toString()
                            )
                    );
                    log.info("Marked order {} as failed via webhook", order.getId());
                }
            });
        }
    }
}

