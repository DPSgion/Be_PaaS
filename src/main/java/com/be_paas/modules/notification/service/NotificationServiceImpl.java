package com.be_paas.modules.notification.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.notification.dto.NotificationResponse;
import com.be_paas.modules.notification.entity.Notification;
import com.be_paas.modules.notification.entity.NotificationType;
import com.be_paas.modules.notification.repository.NotificationRepository;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationServiceImpl implements NotificationService{

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String username) {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 phút

        // Khởi tạo List nếu User này chưa có, sau đó nhét Emitter của Tab hiện tại vào
        userEmitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Lập trình hành động Dọn rác
        Runnable onDisconnect = () -> {
            List<SseEmitter> emitters = userEmitters.get(username);
            if (emitters != null) {
                // CHỈ XÓA ĐÚNG CÁI EMITTER CỦA TAB VỪA ĐÓNG
                emitters.remove(emitter);
                // Nếu User đóng hết toàn bộ các tab thì dọn luôn cái key cho nhẹ RAM
                if (emitters.isEmpty()) {
                    userEmitters.remove(username);
                }
            }
        };

        // Gắn cờ dọn rác
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError((e) -> onDisconnect.run());

        // Bắn tín hiệu mồi
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected Realtime SSE for " + username));
        } catch (IOException e) {
            onDisconnect.run();
        }

        return emitter;
    }

    @Override
    public void sendNotification(Integer userId, String username, Integer projectId, String title, String message, NotificationType type) {
        // 1. Lưu thông báo xuống Database
        Notification notification = Notification.builder()
                .userId(userId)
                .projectId(projectId)
                .title(title)
                .message(message)
                .type(type)
                .build();

        Notification savedNotif = notificationRepository.save(notification);

        // 2. Map sang DTO
        NotificationResponse responsePayload = new NotificationResponse(
                savedNotif.getId(),
                savedNotif.getProjectId(),
                savedNotif.getTitle(),
                savedNotif.getMessage(),
                savedNotif.getType(),
                savedNotif.isRead(),
                savedNotif.getCreatedAt()
        );

        // ==========================================
        // 3. SỬA GẮT: BẮN THÔNG BÁO CHO TẤT CẢ CÁC TAB ĐANG MỞ CỦA USER
        // ==========================================
        List<SseEmitter> emitters = userEmitters.get(username);
        if (emitters != null) {
            // Dùng vòng lặp for (không dùng stream để dễ catch Exception)
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("NEW_NOTIFICATION")
                            .data(responsePayload));
                } catch (IOException e) {
                    // Nếu tab nào bị lỗi mạng, chỉ cắt ống dẫn của tab đó
                    emitters.remove(emitter);
                }
            }
        }
    }

    @Override
    public PageResponse<NotificationResponse> getHistory(
            String username,
            String search,
            NotificationType type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Chuẩn hóa chuỗi tìm kiếm (xóa khoảng trắng 2 đầu, nếu rỗng thì ép về null)
        String finalSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Gọi thẳng xuống Repository bằng hàm tìm kiếm động
        Page<Notification> notiPage = notificationRepository.searchNotifications(
                currentUser.getId(),
                finalSearch,
                type,
                startDate,
                endDate,
                pageable
        );

        Page<NotificationResponse> responsePage = notiPage.map(noti -> new NotificationResponse(
                noti.getId(),
                noti.getProjectId(),
                noti.getTitle(),
                noti.getMessage(),
                noti.getType(),
                noti.isRead(),
                noti.getCreatedAt()
        ));

        return PageResponse.from(responsePage);
    }

    @Override
    public void markAsRead(Integer notificationId, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng"));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy thông báo"));

        // Bảo mật: Chỉ chủ sở hữu mới được đánh dấu đọc
        if (!notification.getUserId().equals(currentUser.getId())) {
            throw new BusinessException(403, "Bạn không có quyền thao tác trên thông báo này");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng"));

        notificationRepository.markAllAsReadByUserId(currentUser.getId());
    }

}
