package com.pulsepay.wallet_service.repository;

import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
    //Hey spring data instead of generating sql query from the above method i am giving you the query i wanted.
    @Query(
            value = """
          SELECT *
          FROM outbox_events
          WHERE status='PENDING'
          ORDER BY created_at ASC
          LIMIT 100
          FOR UPDATE SKIP LOCKED
""",
            nativeQuery = true
    )
    List<OutboxEvent> findPendingEventsForUpdate();
    @Query(
            value= """
SELECT *
FROM outbox_events
WHERE status="PROCESSING"
AND created_at < : "cutoff"
""",
            nativeQuery = true
    )

    List<OutboxEvent> findStaleProcessingEvents(
            @Param("cutoff")Instant cutoff
            );


}
