package com.peergeek.doubtsession.repository;

import java.util.List;
import com.peergeek.doubtsession.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
	@Query("select e from OutboxEventEntity e where e.published = false and e.retryCount < 5 order by e.createdAt asc")
	List<OutboxEventEntity> findPendingEvents(Pageable pageable);
}

