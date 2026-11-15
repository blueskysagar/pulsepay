package com.pulsepay.notification_service.mapper;

import com.pulsepay.notification_service.dto.response.NotificationResponse;
import com.pulsepay.notification_service.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
