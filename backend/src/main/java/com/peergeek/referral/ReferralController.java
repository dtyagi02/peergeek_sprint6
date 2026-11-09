package com.peergeek.referral;

import jakarta.validation.Valid;
import com.peergeek.common.PageResponse;
import com.peergeek.referral.dto.ReferralConfigDto;
import com.peergeek.referral.dto.ReferralHistoryItemDto;
import com.peergeek.referral.dto.ReferralInviteRequest;
import com.peergeek.referral.dto.ReferralInviteResponse;
import com.peergeek.referral.dto.ReferralSummaryDto;
import com.peergeek.referral.dto.ReferralValidateResponse;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referrals")
public class ReferralController {

	private final ReferralService referralService;
	private final CurrentUserProvider currentUserProvider;

	public ReferralController(ReferralService referralService, CurrentUserProvider currentUserProvider) {
		this.referralService = referralService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/invite")
	public ResponseEntity<ReferralInviteResponse> sendInvitation(
			@Valid @RequestBody ReferralInviteRequest request) {
		Long currentUserId = currentUserProvider.requireCurrentUser().id();
		ReferralInviteResponse response = referralService.sendInvitation(currentUserId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/summary")
	public ResponseEntity<ReferralSummaryDto> getSummary() {
		Long currentUserId = currentUserProvider.requireCurrentUser().id();
		return ResponseEntity.ok(referralService.getSummary(currentUserId));
	}

	@GetMapping("/history")
	public ResponseEntity<PageResponse<ReferralHistoryItemDto>> getHistory(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Long currentUserId = currentUserProvider.requireCurrentUser().id();
		int validPage = Math.max(0, page);
		int validSize = Math.clamp(size, 1, 100);
		Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		return ResponseEntity.ok(referralService.getHistory(currentUserId, pageable));
	}

	@GetMapping("/validate")
	public ResponseEntity<ReferralValidateResponse> validateCode(
			@RequestParam String code,
			@RequestParam(required = false) String email) {
		return ResponseEntity.ok(referralService.validateReferralCode(code, email));
	}

	@GetMapping("/config")
	public ResponseEntity<ReferralConfigDto> getConfig() {
		return ResponseEntity.ok(referralService.getConfig());
	}
}

