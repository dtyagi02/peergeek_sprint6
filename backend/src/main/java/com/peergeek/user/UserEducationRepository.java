package com.peergeek.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEducationRepository extends JpaRepository<UserEducationEntity, Long> {
	List<UserEducationEntity> findByUserIdOrderByStartYearDescCreatedAtDesc(Long userId);

	Optional<UserEducationEntity> findByIdAndUserId(Long id, Long userId);

	void deleteByUserId(Long userId);
}
