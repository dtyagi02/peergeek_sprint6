package com.peergeek.walletpayment.wallet;

import com.peergeek.walletpayment.exception.BadRequestException;
import com.peergeek.walletpayment.outbox.OutboxEventWriter;
import com.peergeek.walletpayment.transaction.WalletTransactionEntity;
import com.peergeek.walletpayment.transaction.WalletTransactionRepository;
import com.peergeek.walletpayment.transaction.WalletTransactionType;
import com.peergeek.walletpayment.wallet.dto.WalletBalanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository, outboxEventWriter);
    }

    @Test
    void getOrCreateWallet_createsNewWalletWhenNotFound() {
        Long userId = 101L;
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.saveAndFlush(any(WalletEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WalletEntity wallet = walletService.getOrCreateWallet(userId);

        assertNotNull(wallet);
        assertEquals(userId, wallet.getUserId());
        assertEquals(BigDecimal.ZERO.setScale(2), wallet.getBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), wallet.getReservedBalance());
    }

    @Test
    void credit_increasesBalanceAndSavesTransaction() {
        Long userId = 101L;
        WalletEntity wallet = new WalletEntity(userId, BigDecimal.valueOf(100), "PGC");
        when(walletRepository.findWithLockByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.saveAndFlush(any(WalletEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.credit(
                userId,
                BigDecimal.valueOf(50),
                WalletTransactionType.CREDIT,
                "TEST_REF",
                "REF_1",
                "Test credit"
        );

        assertEquals(BigDecimal.valueOf(150.00).setScale(2), wallet.getBalance());
        verify(transactionRepository).save(any(WalletTransactionEntity.class));
        verify(outboxEventWriter).writeEvent(eq("peergeek.wallet.wallet-credited.v1"), eq("WalletCredited"), any());
    }

    @Test
    void reserveFunds_succeedsWhenBalanceAvailable() {
        Long userId = 101L;
        WalletEntity wallet = new WalletEntity(userId, BigDecimal.valueOf(100), "PGC");
        when(walletRepository.findWithLockByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.saveAndFlush(any(WalletEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.reserveFunds(
                userId,
                BigDecimal.valueOf(40),
                "DOUBT_SESSION",
                "SESSION_1",
                "Session reservation"
        );

        assertEquals(BigDecimal.valueOf(100.00).setScale(2), wallet.getBalance());
        assertEquals(BigDecimal.valueOf(40.00).setScale(2), wallet.getReservedBalance());
        assertEquals(BigDecimal.valueOf(60.00).setScale(2), wallet.getAvailableBalance());
        verify(outboxEventWriter).writeEvent(eq("peergeek.wallet.wallet-reserved.v1"), eq("WalletReserved"), any());
    }

    @Test
    void reserveFunds_throwsBadRequestWhenBalanceInsufficient() {
        Long userId = 101L;
        WalletEntity wallet = new WalletEntity(userId, BigDecimal.valueOf(30), "PGC");
        when(walletRepository.findWithLockByUserId(userId)).thenReturn(Optional.of(wallet));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                walletService.reserveFunds(
                        userId,
                        BigDecimal.valueOf(50),
                        "DOUBT_SESSION",
                        "SESSION_1",
                        "Session reservation"
                )
        );

        assertTrue(ex.getMessage().contains("Insufficient GeekCoins balance"));
    }

    @Test
    void releaseReservedFunds_decrementsReservedBalance() {
        Long userId = 101L;
        WalletEntity wallet = new WalletEntity(userId, BigDecimal.valueOf(100), "PGC");
        wallet.setReservedBalance(BigDecimal.valueOf(50));
        when(walletRepository.findWithLockByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.saveAndFlush(any(WalletEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.releaseReservedFunds(
                userId,
                BigDecimal.valueOf(30),
                "DOUBT_SESSION",
                "SESSION_1",
                "Session release"
        );

        assertEquals(BigDecimal.valueOf(20.00).setScale(2), wallet.getReservedBalance());
        verify(outboxEventWriter).writeEvent(eq("peergeek.wallet.wallet-released.v1"), eq("WalletReleased"), any());
    }

    @Test
    void settleSessionPayment_debitsStudentAndCreditsInstructor() {
        Long studentId = 101L;
        Long instructorId = 202L;

        WalletEntity studentWallet = new WalletEntity(studentId, BigDecimal.valueOf(100), "PGC");
        studentWallet.setReservedBalance(BigDecimal.valueOf(50));

        WalletEntity instructorWallet = new WalletEntity(instructorId, BigDecimal.valueOf(200), "PGC");

        when(walletRepository.findWithLockByUserId(studentId)).thenReturn(Optional.of(studentWallet));
        when(walletRepository.findWithLockByUserId(instructorId)).thenReturn(Optional.of(instructorWallet));
        when(walletRepository.saveAndFlush(any(WalletEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.settleSessionPayment(
                studentId,
                instructorId,
                BigDecimal.valueOf(50), // reserved
                BigDecimal.valueOf(35), // actual charge
                "DOUBT_SESSION",
                "SESSION_1",
                "Doubt session 35m settlement"
        );

        // Student balance should be 100 - 35 = 65, reserved balance 50 - 50 = 0
        assertEquals(BigDecimal.valueOf(65.00).setScale(2), studentWallet.getBalance());
        assertEquals(BigDecimal.valueOf(0.00).setScale(2), studentWallet.getReservedBalance());

        // Instructor balance should be 200 + 35 = 235
        assertEquals(BigDecimal.valueOf(235.00).setScale(2), instructorWallet.getBalance());

        // Debit, release, and credit transactions saved
        verify(transactionRepository, times(3)).save(any(WalletTransactionEntity.class));
        verify(outboxEventWriter).writeEvent(eq("peergeek.wallet.wallet-debited.v1"), eq("WalletDebited"), any());
        verify(outboxEventWriter).writeEvent(eq("peergeek.wallet.wallet-credited.v1"), eq("WalletCredited"), any());
    }

    @Test
    void batchReferralReward_creditsBothWalletsAtomically() {
        Long referralId = 555L;
        Long inviterId = 10L;
        Long newUserId = 20L;

        WalletEntity inviterWallet = new WalletEntity(inviterId, BigDecimal.valueOf(50), "PGC");
        WalletEntity newUserWallet = new WalletEntity(newUserId, BigDecimal.valueOf(0), "PGC");

        when(transactionRepository.existsByReferenceTypeAndReferenceId("REFERRAL", "555")).thenReturn(false);
        when(walletRepository.findWithLockByUserId(inviterId)).thenReturn(Optional.of(inviterWallet));
        when(walletRepository.findWithLockByUserId(newUserId)).thenReturn(Optional.of(newUserWallet));
        when(walletRepository.saveAndFlush(any(WalletEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.batchReferralReward(
                referralId,
                inviterId,
                BigDecimal.valueOf(50),
                "Inviter reward",
                newUserId,
                BigDecimal.valueOf(25),
                "New user reward"
        );

        assertEquals(BigDecimal.valueOf(100.00).setScale(2), inviterWallet.getBalance());
        assertEquals(BigDecimal.valueOf(25.00).setScale(2), newUserWallet.getBalance());

        verify(transactionRepository, times(2)).save(any(WalletTransactionEntity.class));
        verify(outboxEventWriter, times(2)).writeEvent(eq("peergeek.wallet.wallet-credited.v1"), eq("WalletCredited"), any());
    }

    @Test
    void batchReferralReward_isIdempotentOnDuplicateCall() {
        Long referralId = 555L;
        when(transactionRepository.existsByReferenceTypeAndReferenceId("REFERRAL", "555")).thenReturn(true);

        walletService.batchReferralReward(
                referralId,
                10L,
                BigDecimal.valueOf(50),
                "Inviter reward",
                20L,
                BigDecimal.valueOf(25),
                "New user reward"
        );

        // No DB locks or saves executed
        verify(walletRepository, never()).findWithLockByUserId(anyLong());
        verify(walletRepository, never()).saveAndFlush(any());
    }
}

