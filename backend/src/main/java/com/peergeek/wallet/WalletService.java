package com.peergeek.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.wallet.dto.WalletTransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
	private static final Logger log = LoggerFactory.getLogger(WalletService.class);
	public static final String CURRENCY_CODE = "PGC";

	private final WalletRepository walletRepository;
	private final UserRepository userRepository;
	private final WalletTransactionRepository transactionRepository;
	private final com.peergeek.wallet.client.WalletServiceClient walletServiceClient;

	public WalletService(
			WalletRepository walletRepository,
			UserRepository userRepository,
			WalletTransactionRepository transactionRepository,
			@org.springframework.beans.factory.annotation.Autowired(required = false)
			com.peergeek.wallet.client.WalletServiceClient walletServiceClient) {
		this.walletRepository = walletRepository;
		this.userRepository = userRepository;
		this.transactionRepository = transactionRepository;
		this.walletServiceClient = walletServiceClient;
	}

	public WalletService(
			WalletRepository walletRepository,
			UserRepository userRepository,
			WalletTransactionRepository transactionRepository) {
		this(walletRepository, userRepository, transactionRepository, null);
	}

	@Transactional
	public WalletEntity getOrCreateWallet(UserEntity user) {
		return walletRepository.findByUserId(user.getId())
				.orElseGet(() -> {
					try {
						WalletEntity wallet = new WalletEntity(user, BigDecimal.ZERO, CURRENCY_CODE);
						return walletRepository.saveAndFlush(wallet);
					} catch (DataIntegrityViolationException ex) {
						log.warn("Wallet already exists for userId={} due to concurrent creation", user.getId());
						return walletRepository.findByUserId(user.getId())
								.orElseThrow(() -> new IllegalStateException("Failed to retrieve wallet for user: " + user.getId()));
					}
				});
	}

	@Transactional
	public WalletEntity getOrCreateWallet(Long userId) {
		return walletRepository.findByUserId(userId)
				.orElseGet(() -> {
					UserEntity user = userRepository.findById(userId)
							.orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
					return getOrCreateWallet(user);
				});
	}

	@Transactional
	public WalletEntity getOrCreateWalletWithLock(Long userId) {
		WalletEntity wallet = walletRepository.findWithLockByUserId(userId).orElse(null);
		if (wallet == null) {
			getOrCreateWallet(userId);
			wallet = walletRepository.findWithLockByUserId(userId)
					.orElseThrow(() -> new NotFoundException("Wallet not found for userId=" + userId));
		}
		return wallet;
	}

	@Transactional
	public WalletBalanceDto getBalance(Long userId) {
		if (walletServiceClient != null && walletServiceClient.isRemoteEnabled()) {
			try {
				BigDecimal remoteBalance = walletServiceClient.getBalance(userId);
				return new WalletBalanceDto(remoteBalance, CURRENCY_CODE);
			} catch (Exception ex) {
				log.warn("Remote wallet service getBalance failed, falling back to local: {}", ex.getMessage());
			}
		}
		WalletEntity wallet = walletRepository.findByUserId(userId)
				.orElseGet(() -> getOrCreateWallet(userId));
		return new WalletBalanceDto(wallet.getBalance(), wallet.getCurrencyCode());
	}

	@Transactional
	public void reserveFunds(
			Long userId,
			BigDecimal amount,
			String referenceType,
			String referenceId,
			String description) {
		if (walletServiceClient != null && walletServiceClient.isRemoteEnabled()) {
			walletServiceClient.reserveFunds(userId, amount, referenceType, referenceId, description);
			return;
		}
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BadRequestException("Reservation amount must be greater than zero");
		}
		BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
		WalletEntity wallet = getOrCreateWalletWithLock(userId);

		BigDecimal available = wallet.getAvailableBalance();
		if (available.compareTo(scaledAmount) < 0) {
			throw new BadRequestException("Insufficient GeekCoins balance. Available: " + available + " PGC, required: " + scaledAmount + " PGC");
		}

		wallet.setReservedBalance(wallet.getReservedBalance().add(scaledAmount));
		wallet = walletRepository.saveAndFlush(wallet);

		WalletTransactionEntity tx = new WalletTransactionEntity(
				wallet,
				userId,
				WalletTransactionType.RESERVE,
				scaledAmount,
				wallet.getBalance(),
				wallet.getReservedBalance(),
				referenceType,
				referenceId,
				description);
		transactionRepository.save(tx);
		log.info("Reserved {} PGC for userId={}, new reservedBalance={}, ref={}:{}",
				scaledAmount, userId, wallet.getReservedBalance(), referenceType, referenceId);
	}

	@Transactional
	public void releaseReservedFunds(
			Long userId,
			BigDecimal amount,
			String referenceType,
			String referenceId,
			String description) {
		if (walletServiceClient != null && walletServiceClient.isRemoteEnabled()) {
			walletServiceClient.releaseReservedFunds(userId, amount, referenceType, referenceId, description);
			return;
		}
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
		WalletEntity wallet = getOrCreateWalletWithLock(userId);

		BigDecimal toRelease = scaledAmount.min(wallet.getReservedBalance());
		wallet.setReservedBalance(wallet.getReservedBalance().subtract(toRelease));
		wallet = walletRepository.saveAndFlush(wallet);

		WalletTransactionEntity tx = new WalletTransactionEntity(
				wallet,
				userId,
				WalletTransactionType.RELEASE,
				toRelease,
				wallet.getBalance(),
				wallet.getReservedBalance(),
				referenceType,
				referenceId,
				description);
		transactionRepository.save(tx);
		log.info("Released {} PGC for userId={}, new reservedBalance={}, ref={}:{}",
				toRelease, userId, wallet.getReservedBalance(), referenceType, referenceId);
	}

	@Transactional
	public void settleSessionPayment(
			Long studentUserId,
			Long instructorUserId,
			BigDecimal reservedAmount,
			BigDecimal chargeAmount,
			String referenceType,
			String referenceId,
			String description) {
		if (walletServiceClient != null && walletServiceClient.isRemoteEnabled()) {
			walletServiceClient.settleSessionPayment(studentUserId, instructorUserId, reservedAmount, chargeAmount, referenceType, referenceId, description);
			return;
		}
		BigDecimal scaledReserved = (reservedAmount != null ? reservedAmount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
		BigDecimal scaledCharge = (chargeAmount != null ? chargeAmount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

		// Order lock acquisition by user ID to prevent database deadlocks
		Long firstId = studentUserId.compareTo(instructorUserId) < 0 ? studentUserId : instructorUserId;
		Long secondId = firstId.equals(studentUserId) ? instructorUserId : studentUserId;

		WalletEntity firstWallet = getOrCreateWalletWithLock(firstId);
		WalletEntity secondWallet = getOrCreateWalletWithLock(secondId);

		WalletEntity studentWallet = firstId.equals(studentUserId) ? firstWallet : secondWallet;
		WalletEntity instructorWallet = firstId.equals(instructorUserId) ? firstWallet : secondWallet;

		// 1. Release reserved amount from student's reserved_balance
		BigDecimal toReleaseFromReserved = scaledReserved.min(studentWallet.getReservedBalance());
		studentWallet.setReservedBalance(studentWallet.getReservedBalance().subtract(toReleaseFromReserved));

		// 2. Debit actual charge from student balance
		if (scaledCharge.compareTo(BigDecimal.ZERO) > 0) {
			studentWallet.setBalance(studentWallet.getBalance().subtract(scaledCharge));
		}
		studentWallet = walletRepository.saveAndFlush(studentWallet);

		if (scaledCharge.compareTo(BigDecimal.ZERO) > 0) {
			WalletTransactionEntity debitTx = new WalletTransactionEntity(
					studentWallet,
					studentUserId,
					WalletTransactionType.DEBIT,
					scaledCharge,
					studentWallet.getBalance(),
					studentWallet.getReservedBalance(),
					referenceType,
					referenceId,
					description != null ? description : "Doubt session charge");
			transactionRepository.save(debitTx);
		}

		BigDecimal unusedRefund = scaledReserved.subtract(scaledCharge);
		if (unusedRefund.compareTo(BigDecimal.ZERO) > 0) {
			WalletTransactionEntity releaseTx = new WalletTransactionEntity(
					studentWallet,
					studentUserId,
					WalletTransactionType.RELEASE,
					unusedRefund,
					studentWallet.getBalance(),
					studentWallet.getReservedBalance(),
					referenceType,
					referenceId,
					"Released unused reservation for doubt session");
			transactionRepository.save(releaseTx);
		}

		// 3. Credit instructor
		if (scaledCharge.compareTo(BigDecimal.ZERO) > 0) {
			instructorWallet.setBalance(instructorWallet.getBalance().add(scaledCharge));
			instructorWallet = walletRepository.saveAndFlush(instructorWallet);

			WalletTransactionEntity creditTx = new WalletTransactionEntity(
					instructorWallet,
					instructorUserId,
					WalletTransactionType.CREDIT,
					scaledCharge,
					instructorWallet.getBalance(),
					instructorWallet.getReservedBalance(),
					referenceType,
					referenceId,
					"Instructor payout for doubt session");
			transactionRepository.save(creditTx);
		}

		log.info("Settled session payment ref={}:{} student={} charge={} reserved={} instructor={}",
				referenceType, referenceId, studentUserId, scaledCharge, scaledReserved, instructorUserId);
	}

	@Transactional
	public void credit(
			Long userId,
			BigDecimal amount,
			WalletTransactionType type,
			String referenceType,
			String referenceId,
			String description) {
		if (walletServiceClient != null && walletServiceClient.isRemoteEnabled()) {
			walletServiceClient.credit(userId, amount, type != null ? type.name() : "CREDIT", referenceType, referenceId, description);
			return;
		}
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BadRequestException("Credit amount must be greater than zero");
		}
		BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
		WalletEntity wallet = getOrCreateWalletWithLock(userId);
		wallet.setBalance(wallet.getBalance().add(scaledAmount));
		wallet = walletRepository.saveAndFlush(wallet);

		WalletTransactionEntity creditTx = new WalletTransactionEntity(
				wallet,
				userId,
				type,
				scaledAmount,
				wallet.getBalance(),
				wallet.getReservedBalance(),
				referenceType,
				referenceId,
				description);
		transactionRepository.save(creditTx);
		log.info("Credited {} PGC for userId={}, new balance={}, type={}, ref={}:{}",
				scaledAmount, userId, wallet.getBalance(), type, referenceType, referenceId);
	}

	@Transactional
	public void batchReferralReward(
			Long inviterUserId,
			BigDecimal inviterAmount,
			Long newUserId,
			BigDecimal newUserAmount,
			String referralId,
			String inviterDescription,
			String newUserDescription) {
		if (walletServiceClient != null && walletServiceClient.isRemoteEnabled()) {
			walletServiceClient.batchReferralReward(inviterUserId, inviterAmount, newUserId, newUserAmount, referralId, inviterDescription, newUserDescription);
			return;
		}
		credit(inviterUserId, inviterAmount, WalletTransactionType.REFERRAL_INVITER_REWARD, "REFERRAL", referralId, inviterDescription);
		credit(newUserId, newUserAmount, WalletTransactionType.REFERRAL_NEW_USER_REWARD, "REFERRAL", referralId, newUserDescription);
	}

	@Transactional(readOnly = true)
	public Page<WalletTransactionDto> getTransactions(Long userId, Pageable pageable) {
		return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
				.map(WalletTransactionDto::from);
	}
}
