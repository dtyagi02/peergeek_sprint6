package com.peergeek.walletpayment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.walletpayment.security.JwtService;
import com.peergeek.walletpayment.transaction.WalletTransactionType;
import com.peergeek.walletpayment.wallet.WalletEntity;
import com.peergeek.walletpayment.wallet.WalletRepository;
import com.peergeek.walletpayment.wallet.dto.CreditWalletRequest;
import com.peergeek.walletpayment.wallet.dto.ReleaseFundsRequest;
import com.peergeek.walletpayment.wallet.dto.ReserveFundsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletControllerIntegrationTest {

    private static final String GATEWAY_SECRET = "pg-internal-gateway-secret-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private com.peergeek.walletpayment.transaction.WalletTransactionRepository transactionRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void unauthenticatedRequestReturns4xx() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/balance"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void spoofedUserIdWithoutGatewaySecretIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/balance")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void authenticatedRequestWithJwtReturnsBalance() throws Exception {
        Long testUserId = 42L;
        String token = jwtService.generateToken(testUserId, "STUDENT");

        mockMvc.perform(get("/api/v1/wallet/balance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currencyCode").value("PGC"));
    }

    @Test
    void internalWalletOperationsRequireGatewaySecret() throws Exception {
        // Without header -> 403 Forbidden
        mockMvc.perform(get("/api/v1/internal/wallet/42/balance"))
                .andExpect(status().isForbidden());

        // With valid header -> 200 OK
        mockMvc.perform(get("/api/v1/internal/wallet/42/balance")
                        .header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void endToEndWalletLifecycleViaInternalAndUserApis() throws Exception {
        Long testUserId = 77L;
        String token = jwtService.generateToken(testUserId, "STUDENT");

        // 1. Credit wallet via internal endpoint
        CreditWalletRequest creditReq = new CreditWalletRequest(
                testUserId,
                BigDecimal.valueOf(200.00),
                WalletTransactionType.CREDIT,
                "ORDER",
                "ORD_77",
                "Initial topup credit"
        );

        mockMvc.perform(post("/api/v1/internal/wallet/credit")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREDITED"));

        // 2. User checks balance
        mockMvc.perform(get("/api/v1/wallet/balance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.0));

        // 3. Doubt session reserves 100 PGC
        ReserveFundsRequest reserveReq = new ReserveFundsRequest(
                testUserId,
                BigDecimal.valueOf(100.00),
                "DOUBT_SESSION",
                "SESS_77",
                "Reservation for session"
        );

        mockMvc.perform(post("/api/v1/internal/wallet/reservations")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        // 4. Release 50 PGC
        ReleaseFundsRequest releaseReq = new ReleaseFundsRequest(
                testUserId,
                BigDecimal.valueOf(50.00),
                "DOUBT_SESSION",
                "SESS_77_RELEASE",
                "Release partial reservation"
        );

        mockMvc.perform(post("/api/v1/internal/wallet/reservations/release")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        // 5. User checks transactions history
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }
}

