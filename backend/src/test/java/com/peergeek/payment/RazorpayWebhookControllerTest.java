package com.peergeek.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RazorpayWebhookControllerTest {

	@Mock
	private PaymentService paymentService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		RazorpayWebhookController controller = new RazorpayWebhookController(paymentService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	@DisplayName("POST /api/v1/payments/razorpay/webhook delegates to paymentService when signature is present")
	void webhookWithSignature() throws Exception {
		String payload = "{\"event\":\"payment.captured\"}";
		String signature = "test_signature_abc";

		mockMvc.perform(post("/api/v1/payments/razorpay/webhook")
						.header("X-Razorpay-Signature", signature)
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));

		verify(paymentService).processWebhook(payload, signature);
	}
}

