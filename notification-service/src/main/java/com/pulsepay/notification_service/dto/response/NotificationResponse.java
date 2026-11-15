package com.pulsepay.notification_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID transferId,
        String notificationType,
        String message,
        Instant readAt,
        Instant createdAt
) {
}
