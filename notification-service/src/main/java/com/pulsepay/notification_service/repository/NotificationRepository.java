package com.pulsepay.notification_service.repository;

import com.pulsepay.notification_service.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findAllByRecipientAccountIdOrderByCreatedAtDesc(
            UUID recipientAccountId,
            Pageable pageable
    );

    boolean existsByEventIdAndRecipientAccountId(
            String eventId,
            UUID recipientAccountId
    );
    Optional<Notification> findByIdAndRecipientAccountId(UUID id, UUID recipientAccountId);
    //Normally Springdata JPA is used to retrieve the data but this annotation tells spring this query is changing the database
    // JPQL does not support PostgreSQL's ON CONFLICT DO NOTHING syntax,
// so we use a native query to execute PostgreSQL SQL directly.
// Flyway's unique constraint prevents duplicate notifications.
// ON CONFLICT DO NOTHING skips duplicates instead of throwing an error.
    @Modifying
    @Query(value = """
        INSERT INTO notifications (
            id,
            event_id,
            transfer_id,
            recipient_account_id,
            notification_type,
            message,
            created_at
        )
        VALUES (
            :id,
            :eventId,
            :transferId,
            :recipientAccountId,
            :notificationType,
            :message,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (event_id, recipient_account_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("eventId") String eventId,
            @Param("transferId") UUID transferId,
            @Param("recipientAccountId") UUID recipientAccountId,
            @Param("notificationType") String notificationType,
            @Param("message") String message
    );
    //nativeQuery=true means we execute nativeSQL directly using postgresQl syntax instead of jpql.

}
