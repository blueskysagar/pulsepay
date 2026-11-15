package com.pulsepay.notification_service.service.impl;

import com.pulsepay.notification_service.dto.response.NotificationResponse;
import com.pulsepay.notification_service.entity.Notification;
import com.pulsepay.notification_service.exception.NotificationNotFoundException;
import com.pulsepay.notification_service.mapper.NotificationMapper;
import com.pulsepay.notification_service.messaging.event.TransferCompletedEvent;
import com.pulsepay.notification_service.repository.NotificationRepository;
import com.pulsepay.notification_service.service.NotificationProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationProcessingServiceImpl implements NotificationProcessingService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void processTransferCompleted(
            TransferCompletedEvent event,
            String eventId
    ) {
        String sentMessage = "You sent "
                + event.amount().toPlainString()
                + " "
                + event.currency();
        String recievedMessage = "You received "
                + event.amount().toPlainString()
                + " "
                + event.currency();
        notificationRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventId,
                event.transferId(),
                event.sourceAccountId(),
                "TRANSFER_SENT",
                sentMessage


        );
        notificationRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventId,
                event.transferId(),
                event.destinationAccountId(),
                "TRANSFER_RECEIVED",
                recievedMessage
        );
    }
      @Override
              @Transactional(readOnly = true)
      public Page<NotificationResponse> getMyNotifications(UUID accountId, Pageable pageable) {
        return notificationRepository.findAllByRecipientAccountIdOrderByCreatedAtDesc(accountId,pageable)
                .map(notificationMapper::toResponse);
      }
      @Override
    @Transactional
    public void markAsRead(UUID accountId, UUID notificationId){
        Notification notification = notificationRepository.findByIdAndRecipientAccountId(notificationId,accountId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found."
                ));
        notification.markAsRead(Instant.now());
      }


    }
