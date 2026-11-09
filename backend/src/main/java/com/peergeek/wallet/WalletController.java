package com.peergeek.wallet;

import com.peergeek.security.CurrentUserProvider;
import com.peergeek.wallet.dto.WalletTransactionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

	private final WalletService walletService;
	private final CurrentUserProvider currentUserProvider;

	public WalletController(WalletService walletService, CurrentUserProvider currentUserProvider) {
		this.walletService = walletService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/balance")
	public WalletBalanceDto getBalance() {
		Long userId = currentUserProvider.requireCurrentUser().id();
		return walletService.getBalance(userId);
	}

	@GetMapping("/me")
	public WalletBalanceDto getMe() {
		return getBalance();
	}

	@GetMapping("/transactions")
	public Page<WalletTransactionDto> getTransactions(
			@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		Long userId = currentUserProvider.requireCurrentUser().id();
		return walletService.getTransactions(userId, pageable);
	}
}
