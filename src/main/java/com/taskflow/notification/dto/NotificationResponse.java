package com.taskflow.notification.dto;

import com.taskflow.notification.model.Notification;
import com.taskflow.notification.model.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String message,
        NotificationType type,
        UUID taskId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getMessage(),
                notification.getType(),
                notification.getTaskId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
