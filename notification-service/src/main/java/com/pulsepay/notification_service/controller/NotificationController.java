package com.pulsepay.notification_service.controller;

import com.pulsepay.notification_service.dto.response.NotificationResponse;
import com.pulsepay.notification_service.service.NotificationProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationProcessingService notificationProcessingService;
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>>getMyNotifications(@AuthenticationPrincipal String accountId, @PageableDefault(size=20)Pageable pageable){
        UUID authenticatedAccountId = UUID.fromString(accountId);
        Page<NotificationResponse>notifications = notificationProcessingService.getMyNotifications(authenticatedAccountId,pageable);
        return ResponseEntity.ok(notifications);
    }


    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal String accountId,
            @PathVariable UUID notificationId
    ) {
        UUID authenticatedAccountId = UUID.fromString(accountId);

        notificationProcessingService.markAsRead(
                authenticatedAccountId,
                notificationId
        );

        return ResponseEntity.noContent().build();
    }

}
