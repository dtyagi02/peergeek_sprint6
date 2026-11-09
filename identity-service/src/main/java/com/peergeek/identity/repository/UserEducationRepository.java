package com.peergeek.identity.repository;

import java.util.List;
import java.util.Optional;
import com.peergeek.identity.entity.UserEducationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserEducationRepository extends JpaRepository<UserEducationEntity, Long> {
	List<UserEducationEntity> findByUserIdOrderByStartYearDescCreatedAtDesc(Long userId);

	Optional<UserEducationEntity> findByIdAndUserId(Long id, Long userId);

	void deleteByUserId(Long userId);
}

