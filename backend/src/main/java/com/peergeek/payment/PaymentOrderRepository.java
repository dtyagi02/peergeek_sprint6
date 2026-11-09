package com.peergeek.payment;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, Long> {

	Optional<PaymentOrderEntity> findByRazorpayOrderId(String razorpayOrderId);

	Optional<PaymentOrderEntity> findByRazorpayPaymentId(String razorpayPaymentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PaymentOrderEntity p WHERE p.id = :id")
	Optional<PaymentOrderEntity> findWithLockById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PaymentOrderEntity p WHERE p.razorpayOrderId = :razorpayOrderId")
	Optional<PaymentOrderEntity> findWithLockByRazorpayOrderId(@Param("razorpayOrderId") String razorpayOrderId);

	Page<PaymentOrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

