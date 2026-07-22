package com.be_paas.modules.notification.service;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.notification.dto.NotificationResponse;
import com.be_paas.modules.notification.entity.NotificationType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

public interface NotificationService {
    SseEmitter subscribe(String username);
    void sendNotification(Integer userId, String username, Integer projectId, String title, String message, NotificationType type);

    PageResponse<NotificationResponse> getHistory(
            String username,
            String search,
            NotificationType type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size);
    void markAsRead(Integer notificationId, String username);
    void markAllAsRead(String username);
}
