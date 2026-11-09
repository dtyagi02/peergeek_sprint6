package com.peergeek.walletpayment.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, Long> {

    Optional<PaymentOrderEntity> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentOrderEntity> findByRazorpayPaymentId(String razorpayPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrderEntity p WHERE p.id = :id")
    Optional<PaymentOrderEntity> findWithLockById(@Param("id") Long id);

    List<PaymentOrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}

