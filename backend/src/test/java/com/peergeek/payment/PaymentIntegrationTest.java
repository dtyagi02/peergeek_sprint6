package com.peergeek.payment;

import java.math.BigDecimal;
import java.util.UUID;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import com.peergeek.wallet.WalletService;
import com.peergeek.wallet.WalletTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private WalletService walletService;

	private UserEntity createUser() {
		String uid = UUID.randomUUID().toString().substring(0, 8);
		UserEntity user = new UserEntity("payer_" + uid + "@example.com", "hash", "Payer " + uid, "payer_" + uid, true);
		user.setRole(UserRole.USER);
		user.changeMode(UserMode.STUDENT);
		return users.save(user);
	}

	@Test
	@DisplayName("GET /api/v1/wallet/topup/packages requires authentication")
	void getPackages_requiresAuth() throws Exception {
		mockMvc.perform(get("/api/v1/wallet/topup/packages"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("GET /api/v1/wallet/topup/packages returns 4 configured packages when authenticated")
	void getPackages_authenticatedReturnsList() throws Exception {
		UserEntity user = createUser();
		String token = jwtService.issue(user);

		mockMvc.perform(get("/api/v1/wallet/topup/packages")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].id").value("pkg_100"))
				.andExpect(jsonPath("$[0].pgcAmount").value(100))
				.andExpect(jsonPath("$[0].inrAmount").value(100))
				.andExpect(jsonPath("$[1].id").value("pkg_250"))
				.andExpect(jsonPath("$[2].id").value("pkg_500"))
				.andExpect(jsonPath("$[3].id").value("pkg_1000"));
	}

	@Test
	@DisplayName("GET /api/v1/wallet/transactions returns paginated transactions")
	void getTransactions_authenticated() throws Exception {
		UserEntity user = createUser();
		String token = jwtService.issue(user);

		walletService.credit(
				user.getId(),
				new BigDecimal("250.00"),
				WalletTransactionType.RAZORPAY_TOPUP,
				"RAZORPAY_PAYMENT",
				"pay_test_integ_123",
				"GeekCoins Recharge: 250 PGC (INR 250)"
		);

		mockMvc.perform(get("/api/v1/wallet/transactions?page=0&size=10")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].type").value("RAZORPAY_TOPUP"))
				.andExpect(jsonPath("$.content[0].typeLabel").value("Recharge"))
				.andExpect(jsonPath("$.content[0].amount").value(250.0))
				.andExpect(jsonPath("$.content[0].referenceId").value("pay_test_integ_123"));
	}

	@Test
	@DisplayName("POST /api/v1/payments/razorpay/webhook is accessible anonymously (returns 400 when missing signature, NOT 401)")
	void webhook_isPublicEndpoint() throws Exception {
		mockMvc.perform(post("/api/v1/payments/razorpay/webhook")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Missing X-Razorpay-Signature header"));
	}
}

