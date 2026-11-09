package com.peergeek.notification;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

	Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Page<NotificationEntity> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

	long countByUserIdAndIsReadFalse(Long userId);

	Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndEventKey(Long userId, String eventKey);

	@Modifying
	@Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :now WHERE n.userId = :userId AND n.isRead = false")
	int markAllAsRead(@Param("userId") Long userId, @Param("now") Instant now);
}
