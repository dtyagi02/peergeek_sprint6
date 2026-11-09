package com.peergeek.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectRepository extends JpaRepository<UserProjectEntity, Long> {
	List<UserProjectEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<UserProjectEntity> findByIdAndUserId(Long id, Long userId);

	void deleteByUserId(Long userId);
}
