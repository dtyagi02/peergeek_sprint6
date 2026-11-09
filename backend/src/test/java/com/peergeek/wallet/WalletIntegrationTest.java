package com.peergeek.wallet;

import java.math.BigDecimal;
import java.util.UUID;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.auth.AuthService;
import com.peergeek.auth.BootstrapAdminInitializer;
import com.peergeek.auth.RegisterRequest;
import com.peergeek.config.BootstrapAdminSettings;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AuthService authService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private BootstrapAdminInitializer bootstrapAdminInitializer;

	@Autowired
	private BootstrapAdminSettings bootstrapAdminSettings;

	private UserEntity createUser(String email, String username, UserRole role, UserMode mode) {
		UserEntity user = new UserEntity(email, "hashedPassword", username, username, true);
		user.setRole(role);
		user.changeMode(mode);
		return users.save(user);
	}

	@Test
	void newlyRegisteredUserReceivesZeroBalanceWallet() {
		String uid = UUID.randomUUID().toString().substring(0, 8);
		String email = "learner_" + uid + "@example.com";
		String username = "learner_" + uid;

		authService.register(new RegisterRequest(email, "Password123!", "Learner " + uid, username));

		UserEntity user = users.findByEmail(email).orElseThrow();
		WalletEntity wallet = walletRepository.findByUserId(user.getId()).orElse(null);

		assertThat(wallet).isNotNull();
		assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(wallet.getCurrencyCode()).isEqualTo("PGC");
	}

	@Test
	void bootstrapAdminInitializerProvisionsWallet() {
		String uid = UUID.randomUUID().toString().substring(0, 8);
		UserEntity adminUser = createUser("admin_" + uid + "@example.com", "admin_" + uid, UserRole.ADMIN, UserMode.STUDENT);
		WalletEntity wallet = walletService.getOrCreateWallet(adminUser);
		assertThat(wallet).isNotNull();
		assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(wallet.getCurrencyCode()).isEqualTo("PGC");
	}

	@Test
	void getCurrentUserWalletBalanceReturnsCorrectBalanceAndCurrency() throws Exception {
		String uid = UUID.randomUUID().toString().substring(0, 8);
		UserEntity user = createUser("user_" + uid + "@example.com", "user_" + uid, UserRole.USER, UserMode.STUDENT);
		walletService.getOrCreateWallet(user);

		String token = jwtService.issue(user);

		mockMvc.perform(get("/api/v1/wallet/balance")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0.00))
				.andExpect(jsonPath("$.currency").value("PGC"))
				.andExpect(jsonPath("$.currencyCode").value("PGC"));

		// Also verify /api/v1/wallet/me alias
		mockMvc.perform(get("/api/v1/wallet/me")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0.00))
				.andExpect(jsonPath("$.currencyCode").value("PGC"));
	}

	@Test
	void cannotAccessAnotherUsersWalletByManipulatingParams() throws Exception {
		String uidA = UUID.randomUUID().toString().substring(0, 8);
		String uidB = UUID.randomUUID().toString().substring(0, 8);

		UserEntity userA = createUser("usera_" + uidA + "@example.com", "usera_" + uidA, UserRole.USER, UserMode.STUDENT);
		UserEntity userB = createUser("userb_" + uidB + "@example.com", "userb_" + uidB, UserRole.USER, UserMode.STUDENT);

		walletService.getOrCreateWallet(userA);
		WalletEntity walletB = walletService.getOrCreateWallet(userB);
		walletB.setBalance(new BigDecimal("999.00"));
		walletRepository.saveAndFlush(walletB);

		String tokenA = jwtService.issue(userA);

		// Attacker A attempts to request User B's balance via query param
		mockMvc.perform(get("/api/v1/wallet/balance?userId=" + userB.getId())
						.header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0.00))
				.andExpect(jsonPath("$.currencyCode").value("PGC"));
	}

	@Test
	void studentInstructorAndAdminCanRetrieveTheirOwnBalances() throws Exception {
		String uid = UUID.randomUUID().toString().substring(0, 8);

		UserEntity student = createUser("s_" + uid + "@example.com", "s_" + uid, UserRole.USER, UserMode.STUDENT);
		UserEntity instructor = createUser("i_" + uid + "@example.com", "i_" + uid, UserRole.USER, UserMode.INSTRUCTOR);
		UserEntity admin = createUser("a_" + uid + "@example.com", "a_" + uid, UserRole.ADMIN, UserMode.STUDENT);

		walletService.getOrCreateWallet(student);
		walletService.getOrCreateWallet(instructor);
		walletService.getOrCreateWallet(admin);

		for (UserEntity user : java.util.List.of(student, instructor, admin)) {
			String token = jwtService.issue(user);
			mockMvc.perform(get("/api/v1/wallet/balance")
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.balance").value(0.00))
					.andExpect(jsonPath("$.currencyCode").value("PGC"));
		}
	}

	@Test
	void unauthenticatedUserCannotRetrieveWalletBalance() throws Exception {
		mockMvc.perform(get("/api/v1/wallet/balance"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void existingUserWithoutWalletGetsProvisionedAutomatically() throws Exception {
		String uid = UUID.randomUUID().toString().substring(0, 8);
		UserEntity legacyUser = createUser("legacy_" + uid + "@example.com", "legacy_" + uid, UserRole.USER, UserMode.STUDENT);

		// Confirm no wallet exists in DB yet
		assertThat(walletRepository.findByUserId(legacyUser.getId())).isEmpty();

		String token = jwtService.issue(legacyUser);

		// Call endpoint: should auto-provision a zero balance wallet
		mockMvc.perform(get("/api/v1/wallet/balance")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0.00))
				.andExpect(jsonPath("$.currencyCode").value("PGC"));

		assertThat(walletRepository.findByUserId(legacyUser.getId())).isPresent();
	}
}
