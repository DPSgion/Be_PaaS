package com.be_paas.modules.notification.dto;

import com.be_paas.modules.notification.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record NotificationResponse(
        Integer id,
        Integer projectId,
        String title,
        String message,
        NotificationType type,
        boolean isRead,

        @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
        LocalDateTime createdAt
) {
}
