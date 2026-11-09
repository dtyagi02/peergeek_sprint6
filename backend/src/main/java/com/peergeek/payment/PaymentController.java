package com.peergeek.payment;

import java.util.List;
import com.peergeek.payment.dto.CreateOrderRequest;
import com.peergeek.payment.dto.CreateOrderResponse;
import com.peergeek.payment.dto.PaymentVerificationRequest;
import com.peergeek.payment.dto.PaymentVerificationResponse;
import com.peergeek.payment.dto.RechargePackageDto;
import com.peergeek.payment.pricing.RechargePricingService;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
		Long userId = currentUserProvider.requireCurrentUser().id();
		CreateOrderResponse response = paymentService.createTopupOrder(userId, request.packageId());
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

