package com.peergeek.walletpayment.payment;

import com.peergeek.walletpayment.payment.dto.CreateOrderRequest;
import com.peergeek.walletpayment.payment.dto.CreateOrderResponse;
import com.peergeek.walletpayment.payment.dto.PaymentVerificationRequest;
import com.peergeek.walletpayment.payment.dto.PaymentVerificationResponse;
import com.peergeek.walletpayment.payment.dto.RechargePackageDto;
import com.peergeek.walletpayment.payment.pricing.RechargePricingService;
import com.peergeek.walletpayment.security.CurrentUser;
import com.peergeek.walletpayment.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet/topup")
public class PaymentController {

    private final PaymentService paymentService;
    private final RechargePricingService pricingService;
    private final CurrentUserProvider currentUserProvider;

    public PaymentController(
            PaymentService paymentService,
            RechargePricingService pricingService,
            CurrentUserProvider currentUserProvider) {
        this.paymentService = paymentService;
        this.pricingService = pricingService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/packages")
    public ResponseEntity<List<RechargePackageDto>> getPackages() {
        List<RechargePackageDto> packages = pricingService.getAllPackages()
                .stream()
                .map(RechargePackageDto::from)
                .toList();
        return ResponseEntity.ok(packages);
    }

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        CreateOrderResponse response = paymentService.createTopupOrder(
                user.id(),
                request.packageId(),
                "user_" + user.id() + "@peergeek.local",
                "PeerGeek Learner"
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request) {
        Long userId = currentUserProvider.requireCurrentUser().id();
        PaymentVerificationResponse response = paymentService.verifyTopupPayment(userId, request);
        return ResponseEntity.ok(response);
    }
}

