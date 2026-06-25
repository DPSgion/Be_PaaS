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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationServiceImpl implements NotificationService{

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String username) {
        // Mở kết nối sống trong 30 phút. User online web liên tục thì luồng này giữ nguyên.
        SseEmitter emitter = new SseEmitter(1800000L);

        userEmitters.put(username, emitter);

        // Dọn dẹp bộ nhớ nếu kết nối đứt
        emitter.onCompletion(() -> userEmitters.remove(username));
        emitter.onTimeout(() -> userEmitters.remove(username));
        emitter.onError((e) -> userEmitters.remove(username));

        // Bắn tín hiệu đầu tiên báo connect thành công (tránh lỗi timeout ban đầu của SSE)
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected Realtime SSE for " + username));
        } catch (IOException e) {
            userEmitters.remove(username);
        }

        return emitter;
    }

    @Override
    public void sendNotification(Integer userId, String username, Integer projectId, String title, String message, NotificationType type) {
        // 1. Lưu thông báo xuống Database để giữ lịch sử
        Notification notification = Notification.builder()
                .userId(userId)
                .projectId(projectId)
                .title(title)
                .message(message)
                .type(type)
                .build();

        Notification savedNotif = notificationRepository.save(notification);

        // 2. MAPPING SANG DTO ĐỂ ÉP KIỂU JSON TRẢ VỀ
        NotificationResponse responsePayload = new NotificationResponse(
                savedNotif.getId(),
                savedNotif.getProjectId(),
                savedNotif.getTitle(),
                savedNotif.getMessage(),
                savedNotif.getType(),
                savedNotif.isRead(),
                savedNotif.getCreatedAt()
        );

        // 3. Lấy ống dẫn của User này ra và bắn DTO đi
        SseEmitter emitter = userEmitters.get(username);
        if (emitter != null) {
            try {
                // Đẩy responsePayload thay vì savedNotif
                emitter.send(SseEmitter.event()
                        .name("NEW_NOTIFICATION")
                        .data(responsePayload));
            } catch (IOException e) {
                userEmitters.remove(username);
            }
        }
    }

    @Override
    public PageResponse<NotificationResponse> getHistory(String username, int page, int size) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng"));

        // Sort theo thời gian tạo giảm dần - mới nhất lên đầu
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Notification> notiPage = notificationRepository.findByUserId(currentUser.getId(), pageable);

        // Map từ Entity sang DTO
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
}
