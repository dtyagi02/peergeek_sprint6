package com.peergeek.identity.repository;

import java.util.List;
import java.util.Optional;
import com.peergeek.identity.entity.UserProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProjectRepository extends JpaRepository<UserProjectEntity, Long> {
	List<UserProjectEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<UserProjectEntity> findByIdAndUserId(Long id, Long userId);

	void deleteByUserId(Long userId);
}

