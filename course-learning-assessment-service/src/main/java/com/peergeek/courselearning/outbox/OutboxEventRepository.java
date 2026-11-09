package com.peergeek.courselearning.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findUnpublishedEvents(Pageable pageable);
}

