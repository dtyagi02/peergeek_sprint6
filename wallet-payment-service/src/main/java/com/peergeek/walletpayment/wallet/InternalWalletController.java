package com.peergeek.walletpayment.wallet;

import com.peergeek.walletpayment.exception.ForbiddenException;
import com.peergeek.walletpayment.wallet.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/wallet")
public class InternalWalletController {

    private static final Logger log = LoggerFactory.getLogger(InternalWalletController.class);

    private final WalletService walletService;
    private final String gatewaySharedSecret;

    public InternalWalletController(
            WalletService walletService,
            @Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySharedSecret) {
        this.walletService = walletService;
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    private void verifyInternalSecret(String secretHeader) {
        if (secretHeader == null || !gatewaySharedSecret.equals(secretHeader)) {
            log.warn("Rejected unauthorized internal wallet API call: invalid X-Gateway-Secret");
            throw new ForbiddenException("Invalid service-to-service credentials");
        }
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<WalletBalanceDto> getBalance(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        return ResponseEntity.ok(walletService.getBalance(userId));
    }

    @PostMapping("/init/{userId}")
    public ResponseEntity<Map<String, Object>> initWallet(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        WalletEntity wallet = walletService.getOrCreateWallet(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "INITIALIZED",
                "userId", userId,
                "walletId", wallet.getId(),
                "balance", wallet.getBalance()
        ));
    }

    @PostMapping("/reservations")
    public ResponseEntity<Map<String, String>> reserveFunds(
            @Valid @RequestBody ReserveFundsRequest request,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        walletService.reserveFunds(
                request.userId(),
                request.amount(),
                request.referenceType(),
                request.referenceId(),
                request.description()
        );
        return ResponseEntity.ok(Map.of("status", "RESERVED"));
    }

    @PostMapping("/reservations/release")
    public ResponseEntity<Map<String, String>> releaseReservedFunds(
            @Valid @RequestBody ReleaseFundsRequest request,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        walletService.releaseReservedFunds(
                request.userId(),
                request.amount(),
                request.referenceType(),
                request.referenceId(),
                request.description()
        );
        return ResponseEntity.ok(Map.of("status", "RELEASED"));
    }

    @PostMapping("/settlements")
    public ResponseEntity<Map<String, String>> settleSessionPayment(
            @Valid @RequestBody SettlePaymentRequest request,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        walletService.settleSessionPayment(
                request.studentUserId(),
                request.instructorUserId(),
                request.reservedAmount(),
                request.chargeAmount(),
                request.referenceType(),
                request.referenceId(),
                request.description()
        );
        return ResponseEntity.ok(Map.of("status", "SETTLED"));
    }

    @PostMapping("/rewards/referral")
    public ResponseEntity<Map<String, String>> batchReferralReward(
            @Valid @RequestBody BatchReferralRewardRequest request,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        walletService.batchReferralReward(
                request.referralId(),
                request.inviterUserId(),
                request.inviterRewardAmount(),
                request.inviterDescription(),
                request.newUserId(),
                request.newUserRewardAmount(),
                request.newUserDescription()
        );
        return ResponseEntity.ok(Map.of("status", "REWARDED"));
    }

    @PostMapping("/credit")
    public ResponseEntity<Map<String, String>> credit(
            @Valid @RequestBody CreditWalletRequest request,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String secretHeader) {
        verifyInternalSecret(secretHeader);
        walletService.credit(
                request.userId(),
                request.amount(),
                request.type(),
                request.referenceType(),
                request.referenceId(),
                request.description()
        );
        return ResponseEntity.ok(Map.of("status", "CREDITED"));
    }
}

