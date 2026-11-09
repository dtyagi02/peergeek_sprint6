package com.peergeek.walletpayment.wallet;

import com.peergeek.walletpayment.exception.BadRequestException;
import com.peergeek.walletpayment.exception.NotFoundException;
import com.peergeek.walletpayment.outbox.OutboxEventWriter;
import com.peergeek.walletpayment.transaction.WalletTransactionEntity;
import com.peergeek.walletpayment.transaction.WalletTransactionRepository;
import com.peergeek.walletpayment.transaction.WalletTransactionType;
import com.peergeek.walletpayment.wallet.dto.WalletBalanceDto;
import com.peergeek.walletpayment.wallet.dto.WalletTransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    public static final String CURRENCY_CODE = "PGC";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final OutboxEventWriter outboxEventWriter;

    public WalletService(
            WalletRepository walletRepository,
            WalletTransactionRepository transactionRepository,
            OutboxEventWriter outboxEventWriter) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public WalletEntity getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    try {
                        WalletEntity wallet = new WalletEntity(userId, BigDecimal.ZERO, CURRENCY_CODE);
                        return walletRepository.saveAndFlush(wallet);
                    } catch (DataIntegrityViolationException ex) {
                        log.warn("Wallet already exists for userId={} due to concurrent creation", userId);
                        return walletRepository.findByUserId(userId)
                                .orElseThrow(() -> new IllegalStateException("Failed to retrieve wallet for user: " + userId));
                    }
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

    @Transactional(readOnly = true)
    public WalletBalanceDto getBalance(Long userId) {
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
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Reservation amount must be greater than zero");
        }

        // Idempotency check: if reservation for this reference already exists, do not duplicate
        if (referenceType != null && referenceId != null) {
            boolean alreadyReserved = transactionRepository.existsByUserIdAndTypeAndReferenceTypeAndReferenceId(
                    userId, WalletTransactionType.RESERVE, referenceType, referenceId);
            if (alreadyReserved) {
                log.info("Reservation ref={}:{} already exists for userId={}, skipping duplicate", referenceType, referenceId, userId);
                return;
            }
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

        outboxEventWriter.writeEvent(
                "peergeek.wallet.wallet-reserved.v1",
                "WalletReserved",
                Map.of(
                        "userId", userId,
                        "amount", scaledAmount,
                        "reservedBalance", wallet.getReservedBalance(),
                        "referenceType", referenceType != null ? referenceType : "",
                        "referenceId", referenceId != null ? referenceId : "",
                        "occurredAt", Instant.now().toString()
                )
        );

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
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Idempotency check
        if (referenceType != null && referenceId != null) {
            boolean alreadyReleased = transactionRepository.existsByUserIdAndTypeAndReferenceTypeAndReferenceId(
                    userId, WalletTransactionType.RELEASE, referenceType, referenceId);
            if (alreadyReleased) {
                log.info("Release ref={}:{} already exists for userId={}, skipping duplicate", referenceType, referenceId, userId);
                return;
            }
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

        outboxEventWriter.writeEvent(
                "peergeek.wallet.wallet-released.v1",
                "WalletReleased",
                Map.of(
                        "userId", userId,
                        "amount", toRelease,
                        "reservedBalance", wallet.getReservedBalance(),
                        "referenceType", referenceType != null ? referenceType : "",
                        "referenceId", referenceId != null ? referenceId : "",
                        "occurredAt", Instant.now().toString()
                )
        );

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

        // Idempotency check: if settlement debit already recorded, do not duplicate
        if (referenceType != null && referenceId != null) {
            boolean alreadySettled = transactionRepository.existsByUserIdAndTypeAndReferenceTypeAndReferenceId(
                    studentUserId, WalletTransactionType.DEBIT, referenceType, referenceId);
            if (alreadySettled) {
                log.info("Settlement ref={}:{} already processed for student={}, skipping duplicate", referenceType, referenceId, studentUserId);
                return;
            }
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

            outboxEventWriter.writeEvent(
                    "peergeek.wallet.wallet-debited.v1",
                    "WalletDebited",
                    Map.of(
                            "userId", studentUserId,
                            "amount", scaledCharge,
                            "balance", studentWallet.getBalance(),
                            "referenceType", referenceType != null ? referenceType : "",
                            "referenceId", referenceId != null ? referenceId : "",
                            "occurredAt", Instant.now().toString()
                    )
            );
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

            outboxEventWriter.writeEvent(
                    "peergeek.wallet.wallet-credited.v1",
                    "WalletCredited",
                    Map.of(
                            "userId", instructorUserId,
                            "amount", scaledCharge,
                            "balance", instructorWallet.getBalance(),
                            "description", "Instructor payout for doubt session",
                            "referenceType", referenceType != null ? referenceType : "",
                            "referenceId", referenceId != null ? referenceId : "",
                            "occurredAt", Instant.now().toString()
                    )
            );
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
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Credit amount must be greater than zero");
        }

        // Idempotency check: if transaction with this referenceId already exists, do not double-credit
        if (referenceType != null && referenceId != null) {
            boolean alreadyProcessed = transactionRepository
                    .existsByUserIdAndReferenceTypeAndReferenceId(userId, referenceType, referenceId);
            if (alreadyProcessed) {
                log.info("Credit ref={}:{} already processed for userId={}, skipping duplicate", referenceType, referenceId, userId);
                return;
            }
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

        outboxEventWriter.writeEvent(
                "peergeek.wallet.wallet-credited.v1",
                "WalletCredited",
                Map.of(
                        "userId", userId,
                        "amount", scaledAmount,
                        "balance", wallet.getBalance(),
                        "description", description != null ? description : "Wallet Credited",
                        "referenceType", referenceType != null ? referenceType : "",
                        "referenceId", referenceId != null ? referenceId : "",
                        "occurredAt", Instant.now().toString()
                )
        );

        log.info("Credited {} PGC for userId={}, new balance={}, type={}, ref={}:{}",
                scaledAmount, userId, wallet.getBalance(), type, referenceType, referenceId);
    }

    @Transactional
    public void batchReferralReward(
            Long referralId,
            Long inviterUserId,
            BigDecimal inviterRewardAmount,
            String inviterDescription,
            Long newUserId,
            BigDecimal newUserRewardAmount,
            String newUserDescription) {

        String refIdStr = String.valueOf(referralId);

        // Check if referral already processed
        if (transactionRepository.existsByReferenceTypeAndReferenceId("REFERRAL", refIdStr)) {
            log.info("Referral reward {} already credited, returning existing idempotent result", referralId);
            return;
        }

        // Lock wallets in deterministic order
        Long firstId = inviterUserId.compareTo(newUserId) < 0 ? inviterUserId : newUserId;
        Long secondId = firstId.equals(inviterUserId) ? newUserId : inviterUserId;

        WalletEntity firstWallet = getOrCreateWalletWithLock(firstId);
        WalletEntity secondWallet = getOrCreateWalletWithLock(secondId);

        WalletEntity inviterWallet = firstId.equals(inviterUserId) ? firstWallet : secondWallet;
        WalletEntity newUserWallet = firstId.equals(newUserId) ? firstWallet : secondWallet;

        // Credit inviter
        BigDecimal scaledInviter = inviterRewardAmount.setScale(2, RoundingMode.HALF_UP);
        inviterWallet.setBalance(inviterWallet.getBalance().add(scaledInviter));
        walletRepository.saveAndFlush(inviterWallet);

        WalletTransactionEntity inviterTx = new WalletTransactionEntity(
                inviterWallet,
                inviterUserId,
                WalletTransactionType.REFERRAL_INVITER_REWARD,
                scaledInviter,
                inviterWallet.getBalance(),
                inviterWallet.getReservedBalance(),
                "REFERRAL",
                refIdStr,
                inviterDescription != null ? inviterDescription : "Referral reward for invitation");
        transactionRepository.save(inviterTx);

        // Credit new user
        BigDecimal scaledNewUser = newUserRewardAmount.setScale(2, RoundingMode.HALF_UP);
        newUserWallet.setBalance(newUserWallet.getBalance().add(scaledNewUser));
        walletRepository.saveAndFlush(newUserWallet);

        WalletTransactionEntity newUserTx = new WalletTransactionEntity(
                newUserWallet,
                newUserId,
                WalletTransactionType.REFERRAL_NEW_USER_REWARD,
                scaledNewUser,
                newUserWallet.getBalance(),
                newUserWallet.getReservedBalance(),
                "REFERRAL",
                refIdStr,
                newUserDescription != null ? newUserDescription : "Welcome referral bonus");
        transactionRepository.save(newUserTx);

        // Transactional Outbox events
        outboxEventWriter.writeEvent(
                "peergeek.wallet.wallet-credited.v1",
                "WalletCredited",
                Map.of(
                        "userId", inviterUserId,
                        "amount", scaledInviter,
                        "balance", inviterWallet.getBalance(),
                        "description", inviterTx.getDescription(),
                        "referenceType", "REFERRAL",
                        "referenceId", refIdStr,
                        "occurredAt", Instant.now().toString()
                )
        );

        outboxEventWriter.writeEvent(
                "peergeek.wallet.wallet-credited.v1",
                "WalletCredited",
                Map.of(
                        "userId", newUserId,
                        "amount", scaledNewUser,
                        "balance", newUserWallet.getBalance(),
                        "description", newUserTx.getDescription(),
                        "referenceType", "REFERRAL",
                        "referenceId", refIdStr,
                        "occurredAt", Instant.now().toString()
                )
        );

        log.info("Batch referral reward processed for referralId={}, inviterId={} (+{} PGC), newUserId={} (+{} PGC)",
                referralId, inviterUserId, scaledInviter, newUserId, scaledNewUser);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionDto> getTransactions(Long userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(WalletTransactionDto::from);
    }
}

