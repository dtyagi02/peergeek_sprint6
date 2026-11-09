package com.peergeek.walletpayment.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.walletpayment.exception.BadRequestException;
import com.peergeek.walletpayment.outbox.OutboxEventWriter;
import com.peergeek.walletpayment.payment.dto.PaymentVerificationRequest;
import com.peergeek.walletpayment.payment.dto.PaymentVerificationResponse;
import com.peergeek.walletpayment.payment.pricing.RechargePricingService;
import com.peergeek.walletpayment.wallet.WalletService;
import com.peergeek.walletpayment.wallet.dto.WalletBalanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private RazorpaySignatureVerifier signatureVerifier;

    @Mock
    private RazorpaySettings settings;

    @Mock
    private WalletService walletService;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private PaymentService paymentService;
    private final RechargePricingService pricingService = new RechargePricingService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentOrderRepository,
                pricingService,
                razorpayService,
                signatureVerifier,
                settings,
                walletService,
                outboxEventWriter,
                objectMapper
        );
    }

    @Test
    void verifyTopupPayment_successfulVerificationCreditsWallet() {
        Long userId = 42L;
        String rzpOrderId = "order_rzp_123";
        String rzpPaymentId = "pay_rzp_456";
        String rzpSignature = "mock_sig_789";

        PaymentOrderEntity order = new PaymentOrderEntity(
                userId, "pkg_100", 10000L, "INR", BigDecimal.valueOf(100), rzpOrderId
        );
        order.setId(1L);

        when(paymentOrderRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(order));
        when(settings.getKeySecret()).thenReturn("mock_key_secret");
        when(signatureVerifier.verifyPaymentSignature(rzpOrderId, rzpPaymentId, rzpSignature, "mock_key_secret"))
                .thenReturn(true);

        when(razorpayService.fetchPayment(rzpPaymentId))
                .thenReturn(new RazorpayService.RazorpayPayment(rzpPaymentId, rzpOrderId, "captured", 10000L, "INR", true));

        when(paymentOrderRepository.findWithLockById(1L)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.findByRazorpayPaymentId(rzpPaymentId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.saveAndFlush(any(PaymentOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletService.getBalance(userId)).thenReturn(new WalletBalanceDto(BigDecimal.valueOf(100), "PGC"));

        PaymentVerificationRequest req = new PaymentVerificationRequest(rzpOrderId, rzpPaymentId, rzpSignature);
        PaymentVerificationResponse response = paymentService.verifyTopupPayment(userId, req);

        assertTrue(response.success());
        assertEquals(PaymentOrderStatus.CAPTURED, response.status());
        assertEquals(BigDecimal.valueOf(100), response.pgcAmount());
        verify(walletService).credit(eq(userId), eq(BigDecimal.valueOf(100)), any(), eq("RAZORPAY_PAYMENT"), eq(rzpPaymentId), any());
        verify(outboxEventWriter).writeEvent(eq("peergeek.wallet.payment-captured.v1"), eq("PaymentCaptured"), any());
    }

    @Test
    void verifyTopupPayment_invalidSignatureThrowsBadRequest() {
        Long userId = 42L;
        String rzpOrderId = "order_rzp_123";
        String rzpPaymentId = "pay_rzp_456";
        String rzpSignature = "bad_sig";

        PaymentOrderEntity order = new PaymentOrderEntity(
                userId, "pkg_100", 10000L, "INR", BigDecimal.valueOf(100), rzpOrderId
        );
        order.setId(1L);

        when(paymentOrderRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(order));
        when(settings.getKeySecret()).thenReturn("mock_key_secret");
        when(signatureVerifier.verifyPaymentSignature(rzpOrderId, rzpPaymentId, rzpSignature, "mock_key_secret"))
                .thenReturn(false);

        PaymentVerificationRequest req = new PaymentVerificationRequest(rzpOrderId, rzpPaymentId, rzpSignature);

        assertThrows(BadRequestException.class, () -> paymentService.verifyTopupPayment(userId, req));
        assertEquals(PaymentOrderStatus.FAILED, order.getStatus());
        verify(paymentOrderRepository).save(order);
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void verifyTopupPayment_alreadyCapturedReturnsIdempotentSuccess() {
        Long userId = 42L;
        String rzpOrderId = "order_rzp_123";

        PaymentOrderEntity order = new PaymentOrderEntity(
                userId, "pkg_100", 10000L, "INR", BigDecimal.valueOf(100), rzpOrderId
        );
        order.setId(1L);
        order.setStatus(PaymentOrderStatus.CAPTURED);
        order.setRazorpayPaymentId("pay_rzp_456");

        when(paymentOrderRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(order));
        when(walletService.getBalance(userId)).thenReturn(new WalletBalanceDto(BigDecimal.valueOf(100), "PGC"));

        PaymentVerificationRequest req = new PaymentVerificationRequest(rzpOrderId, "pay_rzp_456", "any_sig");
        PaymentVerificationResponse response = paymentService.verifyTopupPayment(userId, req);

        assertTrue(response.success());
        assertEquals(PaymentOrderStatus.CAPTURED, response.status());
        verify(signatureVerifier, never()).verifyPaymentSignature(any(), any(), any(), any());
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any());
    }
}

