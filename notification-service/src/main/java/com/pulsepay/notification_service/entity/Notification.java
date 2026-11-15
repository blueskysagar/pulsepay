package com.pulsepay.notification_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter

@Table(name="notifications",
uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_notifications_event_recipient",
                columnNames = {"event_id","recipient_account_id"}
        )
},
indexes = {
        @Index(
                name="idx_notifications_recipient_created",
                columnList = "recipient_account_id, created_at"
   )
})

public class Notification {
    @Id
    private UUID id;
    @Column(name="event_id",nullable = false, length = 255)
    private String eventId;
    @Column(name="transfer_id", nullable = false)
    private UUID transferId;
    @Column(name="recipient_account_id",nullable = false)
    private UUID recipientAccountId;
    @Column(name="notification_type",nullable = false,length = 50)
    private String notificationType;
    @Column(name="message",nullable = false,length=500)
    private String message;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name="created_at",nullable = false)
    private Instant createdAt;
    protected Notification(){

    }
    public Notification(
            UUID id,
            String eventId,
            UUID transferId,
            UUID recipientAccountId,
            String notificationType,
            String message,
            Instant createdAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.transferId = transferId;
        this.recipientAccountId = recipientAccountId;
        this.notificationType = notificationType;
        this.message = message;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getRecipientAccountId() {
        return recipientAccountId;
    }
    public void markAsRead(Instant readAt){
        if(this.readAt==null){
            this.readAt=readAt;
        }
    }

}
