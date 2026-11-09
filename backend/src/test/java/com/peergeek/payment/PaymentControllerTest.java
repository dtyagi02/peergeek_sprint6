package com.peergeek.payment;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.payment.dto.CreateOrderRequest;
import com.peergeek.payment.dto.CreateOrderResponse;
import com.peergeek.payment.dto.PaymentVerificationRequest;
import com.peergeek.payment.dto.PaymentVerificationResponse;
import com.peergeek.payment.pricing.RechargePackage;
import com.peergeek.payment.pricing.RechargePricingService;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

	@Mock
	private PaymentService paymentService;

	@Mock
	private RechargePricingService pricingService;

	@Mock
	private CurrentUserProvider currentUserProvider;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		PaymentController controller = new PaymentController(paymentService, pricingService, currentUserProvider);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	@DisplayName("GET /api/v1/wallet/topup/packages returns packages list")
	void getPackages() throws Exception {
		RechargePackage pkg1 = RechargePackage.of("pkg_100", "Starter", 100, 100, null, "100 PGC");
		RechargePackage pkg2 = RechargePackage.of("pkg_500", "Popular", 500, 500, "Popular", "500 PGC");
		when(pricingService.getAllPackages()).thenReturn(List.of(pkg1, pkg2));

		mockMvc.perform(get("/api/v1/wallet/topup/packages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value("pkg_100"))
				.andExpect(jsonPath("$[0].inrAmount").value(100))
				.andExpect(jsonPath("$[0].pgcAmount").value(100))
				.andExpect(jsonPath("$[1].id").value("pkg_500"))
				.andExpect(jsonPath("$[1].badge").value("Popular"));
	}

	@Test
	@DisplayName("POST /api/v1/wallet/topup/orders creates Razorpay order")
	void createOrder() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));

		CreateOrderResponse response = new CreateOrderResponse(
				1L,
				"order_rzp_123",
				10000L,
				new BigDecimal("100"),
				"INR",
				new BigDecimal("100"),
				"rzp_test_key",
				"pkg_100",
				"user@example.com",
				"Test User"
		);
		when(paymentService.createTopupOrder(42L, "pkg_100")).thenReturn(response);

		CreateOrderRequest request = new CreateOrderRequest("pkg_100");

		mockMvc.perform(post("/api/v1/wallet/topup/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(1))
				.andExpect(jsonPath("$.razorpayOrderId").value("order_rzp_123"))
				.andExpect(jsonPath("$.amountPaise").value(10000))
				.andExpect(jsonPath("$.keyId").value("rzp_test_key"));

		verify(paymentService).createTopupOrder(42L, "pkg_100");
	}

	@Test
	@DisplayName("POST /api/v1/wallet/topup/verify verifies payment and returns updated balance")
	void verifyPayment() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));

		PaymentVerificationResponse response = new PaymentVerificationResponse(
				true,
				1L,
				"order_rzp_123",
				"pay_rzp_456",
				PaymentOrderStatus.CAPTURED,
				new BigDecimal("100"),
				new BigDecimal("250.00"),
				"Recharge successful!"
		);
		when(paymentService.verifyTopupPayment(eq(42L), any())).thenReturn(response);

		PaymentVerificationRequest request = new PaymentVerificationRequest(
				"order_rzp_123",
				"pay_rzp_456",
				"sig_test_789"
		);

		mockMvc.perform(post("/api/v1/wallet/topup/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value("CAPTURED"))
				.andExpect(jsonPath("$.pgcAmount").value(100))
				.andExpect(jsonPath("$.newBalance").value(250.00));
	}
}

