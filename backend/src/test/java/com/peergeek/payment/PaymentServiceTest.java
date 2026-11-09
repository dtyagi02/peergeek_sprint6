package com.peergeek.payment;

import java.math.BigDecimal;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.exception.BadRequestException;
import com.peergeek.payment.dto.CreateOrderResponse;
import com.peergeek.payment.dto.PaymentVerificationRequest;
import com.peergeek.payment.dto.PaymentVerificationResponse;
import com.peergeek.payment.pricing.RechargePackage;
import com.peergeek.payment.pricing.RechargePricingService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.wallet.WalletBalanceDto;
import com.peergeek.wallet.WalletService;
import com.peergeek.wallet.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock
	private PaymentOrderRepository paymentOrderRepository;

	@Mock
	private WalletService walletService;

	@Mock
	private RazorpayService razorpayService;

	@Mock
	private RazorpaySignatureVerifier signatureVerifier;

	@Mock
	private RechargePricingService pricingService;

	@Mock
	private RazorpaySettings settings;

	@Mock
	private UserRepository userRepository;

	private ObjectMapper objectMapper;
	private PaymentService paymentService;

	private final Long testUserId = 42L;
	private UserEntity testUser;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		paymentService = new PaymentService(
				paymentOrderRepository,
				pricingService,
				razorpayService,
				signatureVerifier,
				settings,
				walletService,
				userRepository,
				objectMapper
		);

		testUser = new UserEntity("student@peergeek.com", "hash", "Test Student");
		ReflectionTestUtils.setField(testUser, "id", testUserId);
	}

	@Test
	@DisplayName("createTopupOrder throws exception if package is unknown")
	void createTopupOrder_invalidPackage() {
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(pricingService.getPackage("invalid")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> paymentService.createTopupOrder(testUserId, "invalid"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Invalid recharge package");
	}

	@Test
	@DisplayName("createTopupOrder throws exception if Razorpay is not configured")
	void createTopupOrder_razorpayNotConfigured() {
		RechargePackage pkg = RechargePackage.of("starter", "Starter", 100L, 100L, null, "Starter pack");
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(pricingService.getPackage("starter")).thenReturn(Optional.of(pkg));
		when(razorpayService.createOrder(eq(10000L), eq("INR"), anyString(), any()))
				.thenThrow(new BadRequestException("Payment gateway credentials are not configured"));

		assertThatThrownBy(() -> paymentService.createTopupOrder(testUserId, "starter"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Payment gateway credentials are not configured");
	}

	@Test
	@DisplayName("createTopupOrder successfully creates gateway order and saves PaymentOrderEntity")
	void createTopupOrder_success() {
		RechargePackage pkg = RechargePackage.of("starter", "Starter", 100L, 100L, null, "Starter pack");
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(pricingService.getPackage("starter")).thenReturn(Optional.of(pkg));
		when(settings.getKeyId()).thenReturn("rzp_test_key123");

		RazorpayService.RazorpayOrder rzpOrder = new RazorpayService.RazorpayOrder(
				"order_12345", 10000L, "INR", "receipt_1", "created"
		);
		when(razorpayService.createOrder(eq(10000L), eq("INR"), anyString(), any())).thenReturn(rzpOrder);

		PaymentOrderEntity savedEntity = new PaymentOrderEntity(
				testUserId, "starter", 10000L, "INR", new BigDecimal("100"), "order_12345"
		);
		ReflectionTestUtils.setField(savedEntity, "id", 1L);
		when(paymentOrderRepository.save(any(PaymentOrderEntity.class))).thenReturn(savedEntity);

		CreateOrderResponse response = paymentService.createTopupOrder(testUserId, "starter");

		assertThat(response).isNotNull();
		assertThat(response.razorpayOrderId()).isEqualTo("order_12345");
		assertThat(response.amountPaise()).isEqualTo(10000L);
		assertThat(response.pgcAmount()).isEqualByComparingTo("100");
		assertThat(response.keyId()).isEqualTo("rzp_test_key123");

		ArgumentCaptor<PaymentOrderEntity> captor = ArgumentCaptor.forClass(PaymentOrderEntity.class);
		verify(paymentOrderRepository).save(captor.capture());
		assertThat(captor.getValue().getRazorpayOrderId()).isEqualTo("order_12345");
		assertThat(captor.getValue().getStatus()).isEqualTo(PaymentOrderStatus.CREATED);
	}

	@Test
	@DisplayName("verifyTopupPayment throws BadRequestException when signature is invalid")
	void verifyTopupPayment_invalidSignature() {
		PaymentOrderEntity order = new PaymentOrderEntity(
				testUserId, "starter", 10000L, "INR", new BigDecimal("100"), "order_12345"
		);
		ReflectionTestUtils.setField(order, "id", 1L);

		when(paymentOrderRepository.findByRazorpayOrderId("order_12345")).thenReturn(Optional.of(order));
		when(settings.getKeySecret()).thenReturn("rzp_secret");
		when(signatureVerifier.verifyPaymentSignature("order_12345", "pay_999", "bad_sig", "rzp_secret"))
				.thenReturn(false);

		PaymentVerificationRequest request = new PaymentVerificationRequest("order_12345", "pay_999", "bad_sig");

		assertThatThrownBy(() -> paymentService.verifyTopupPayment(testUserId, request))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Invalid payment signature");

		assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
		verify(paymentOrderRepository).save(order);
	}

	@Test
	@DisplayName("verifyTopupPayment credits wallet on valid captured payment")
	void verifyTopupPayment_success() {
		PaymentOrderEntity order = new PaymentOrderEntity(
				testUserId, "starter", 10000L, "INR", new BigDecimal("100"), "order_12345"
		);
		ReflectionTestUtils.setField(order, "id", 1L);

		when(paymentOrderRepository.findByRazorpayOrderId("order_12345")).thenReturn(Optional.of(order));
		when(settings.getKeySecret()).thenReturn("rzp_secret");
		when(signatureVerifier.verifyPaymentSignature("order_12345", "pay_999", "good_sig", "rzp_secret"))
				.thenReturn(true);

		RazorpayService.RazorpayPayment payment = new RazorpayService.RazorpayPayment(
				"pay_999", "order_12345", "captured", 10000L, "INR", true
		);
		when(razorpayService.fetchPayment("pay_999")).thenReturn(payment);
		when(paymentOrderRepository.findWithLockById(1L)).thenReturn(Optional.of(order));
		when(paymentOrderRepository.findByRazorpayPaymentId("pay_999")).thenReturn(Optional.empty());
		when(paymentOrderRepository.saveAndFlush(any(PaymentOrderEntity.class))).thenAnswer(i -> i.getArgument(0));
		when(walletService.getBalance(testUserId)).thenReturn(new WalletBalanceDto(new BigDecimal("150.00"), "PGC"));

		PaymentVerificationRequest request = new PaymentVerificationRequest("order_12345", "pay_999", "good_sig");
		PaymentVerificationResponse response = paymentService.verifyTopupPayment(testUserId, request);

		assertThat(response.success()).isTrue();
		assertThat(response.status()).isEqualTo(PaymentOrderStatus.CAPTURED);
		assertThat(response.pgcAmount()).isEqualByComparingTo("100");
		assertThat(response.newBalance()).isEqualByComparingTo("150.00");

		verify(walletService).credit(
				eq(testUserId),
				eq(new BigDecimal("100")),
				eq(WalletTransactionType.RAZORPAY_TOPUP),
				eq("RAZORPAY_PAYMENT"),
				eq("pay_999"),
				anyString()
		);
	}

	@Test
	@DisplayName("Idempotency: duplicate verify calls do not credit wallet a second time")
	void verifyTopupPayment_idempotent() {
		PaymentOrderEntity order = new PaymentOrderEntity(
				testUserId, "starter", 10000L, "INR", new BigDecimal("100"), "order_12345"
		);
		ReflectionTestUtils.setField(order, "id", 1L);
		order.setStatus(PaymentOrderStatus.CAPTURED);
		order.setRazorpayPaymentId("pay_999");

		when(paymentOrderRepository.findByRazorpayOrderId("order_12345")).thenReturn(Optional.of(order));
		when(walletService.getBalance(testUserId)).thenReturn(new WalletBalanceDto(new BigDecimal("150.00"), "PGC"));

		PaymentVerificationRequest request = new PaymentVerificationRequest("order_12345", "pay_999", "good_sig");
		PaymentVerificationResponse response = paymentService.verifyTopupPayment(testUserId, request);

		assertThat(response.success()).isTrue();
		assertThat(response.message()).isEqualTo("Recharge already completed successfully");
		// Verify credit was NEVER called!
		verify(walletService, never()).credit(anyLong(), any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("processWebhook correctly processes payment.captured event")
	void processWebhook_paymentCaptured() {
		String webhookPayload = """
				{
				  "event": "payment.captured",
				  "payload": {
				    "payment": {
				      "entity": {
				        "id": "pay_webhook_123",
				        "order_id": "order_webhook_abc",
				        "amount": 25000,
				        "currency": "INR",
				        "status": "captured"
				      }
				    }
				  }
				}
				""";

		when(settings.isWebhookConfigured()).thenReturn(true);
		when(settings.getWebhookSecret()).thenReturn("wh_secret");
		when(signatureVerifier.verifyWebhookSignature(webhookPayload, "valid_sig", "wh_secret")).thenReturn(true);

		PaymentOrderEntity order = new PaymentOrderEntity(
				testUserId, "popular", 25000L, "INR", new BigDecimal("250"), "order_webhook_abc"
		);
		ReflectionTestUtils.setField(order, "id", 2L);

		when(paymentOrderRepository.findByRazorpayOrderId("order_webhook_abc")).thenReturn(Optional.of(order));
		when(paymentOrderRepository.findWithLockById(2L)).thenReturn(Optional.of(order));
		when(paymentOrderRepository.findByRazorpayPaymentId("pay_webhook_123")).thenReturn(Optional.empty());
		when(paymentOrderRepository.saveAndFlush(any(PaymentOrderEntity.class))).thenAnswer(i -> i.getArgument(0));
		when(walletService.getBalance(testUserId)).thenReturn(new WalletBalanceDto(new BigDecimal("300.00"), "PGC"));

		paymentService.processWebhook(webhookPayload, "valid_sig");

		verify(walletService).credit(
				eq(testUserId),
				eq(new BigDecimal("250")),
				eq(WalletTransactionType.RAZORPAY_TOPUP),
				eq("RAZORPAY_PAYMENT"),
				eq("pay_webhook_123"),
				anyString()
		);
		assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CAPTURED);
	}

	@Test
	@DisplayName("processWebhook marks order failed on payment.failed event")
	void processWebhook_paymentFailed() {
		String webhookPayload = """
				{
				  "event": "payment.failed",
				  "payload": {
				    "payment": {
				      "entity": {
				        "id": "pay_fail_999",
				        "order_id": "order_fail_abc",
				        "amount": 25000,
				        "currency": "INR",
				        "status": "failed",
				        "error_description": "Card expired"
				      }
				    }
				  }
				}
				""";

		when(settings.isWebhookConfigured()).thenReturn(true);
		when(settings.getWebhookSecret()).thenReturn("wh_secret");
		when(signatureVerifier.verifyWebhookSignature(webhookPayload, "valid_sig", "wh_secret")).thenReturn(true);

		PaymentOrderEntity order = new PaymentOrderEntity(
				testUserId, "popular", 25000L, "INR", new BigDecimal("250"), "order_fail_abc"
		);
		ReflectionTestUtils.setField(order, "id", 3L);

		when(paymentOrderRepository.findByRazorpayOrderId("order_fail_abc")).thenReturn(Optional.of(order));
		when(paymentOrderRepository.save(any(PaymentOrderEntity.class))).thenAnswer(i -> i.getArgument(0));

		paymentService.processWebhook(webhookPayload, "valid_sig");

		verify(walletService, never()).credit(anyLong(), any(), any(), anyString(), anyString(), anyString());
		assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
		assertThat(order.getFailureReason()).isEqualTo("Card expired");
	}
}
