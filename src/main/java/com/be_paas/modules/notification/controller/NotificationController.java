package com.be_paas.modules.notification.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.notification.dto.NotificationResponse;
import com.be_paas.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping({"", "/"})
    public PageResponse<NotificationResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return notificationService.getHistory(username, page, size);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return notificationService.subscribe(username);
    }

    // =========================================================================
    // 1. GET /notifications -> Lấy danh sách thông báo cũ từ Database (phân trang).
    // 2. PATCH /notifications/{id}/read -> Đánh dấu 1 thông báo là "Đã đọc" (isRead = true).
    // =========================================================================
}
