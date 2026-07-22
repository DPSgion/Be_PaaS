package com.be_paas.modules.notification.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.notification.dto.NotificationResponse;
import com.be_paas.modules.notification.entity.NotificationType;
import com.be_paas.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping({"", "/"})
    public ResponseEntity<PageResponse<NotificationResponse>> getNotifications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        return ResponseEntity.ok(notificationService.getHistory(
                username, search, type, startDate, endDate, page, size
        ));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return notificationService.subscribe(username);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Integer id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        notificationService.markAsRead(id, username);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        notificationService.markAllAsRead(username);
        return ResponseEntity.ok().build();
    }
}
