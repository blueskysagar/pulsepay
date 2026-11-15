package com.pulsepay.notification_service.service;

import com.pulsepay.notification_service.dto.response.NotificationResponse;
import com.pulsepay.notification_service.entity.Notification;
import com.pulsepay.notification_service.messaging.event.TransferCompletedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationProcessingService {
    //In wallet service, payment service we use CreateWalletResponse or CreateTransferResponse but here we dont use it because it is the method called by Listener not by customer making an HTTP request.
    void processTransferCompleted(
            TransferCompletedEvent event,
            String eventId
    );
    Page<NotificationResponse> getMyNotifications(
            UUID accountId,
            Pageable pageable

    );
    void markAsRead(
            UUID accountId,
            UUID notificationId
    );
}
