package com.be_paas.modules.auditlog.service;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.auditlog.dto.AuditLogResponse;
import com.be_paas.modules.auditlog.entity.ActionType;
import com.be_paas.modules.auditlog.entity.AuditLog;
import com.be_paas.modules.auditlog.repository.AuditLogRepository;
import com.be_paas.modules.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class AuditLogServiceImpl implements AuditLogService{

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, UserRepository userRepository) {

        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PageResponse<AuditLogResponse> getAuditLogs(
            String search, String actionTypeStr, String fromDateStr, String toDateStr, int page, int size) {

        String term = (search != null && !search.isBlank()) ? search.strip() : null;

        ActionType actionType = null;
        if (actionTypeStr != null && !actionTypeStr.isBlank() && !actionTypeStr.equalsIgnoreCase("ALL")) {
            try {
                actionType = ActionType.valueOf(actionTypeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Action Type không hợp lệ: " + actionTypeStr);
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;

        try {
            if (fromDateStr != null && !fromDateStr.isBlank()) {
                fromDate = LocalDate.parse(fromDateStr.trim(), formatter).atStartOfDay();
            }
            if (toDateStr != null && !toDateStr.isBlank()) {
                toDate = LocalDate.parse(toDateStr.trim(), formatter).atTime(LocalTime.MAX);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Định dạng ngày không hợp lệ. Vui lòng dùng dd/MM/yyyy");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<AuditLog> logPage = auditLogRepository.searchAuditLogsWithFilter(
                term, actionType, fromDate, toDate, pageable
        );

        return PageResponse.from(logPage.map(this::mapToResponse));
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        // 1. Tìm thông tin người thực hiện hành động
        String actorFormatted = "Hệ thống";
        if (log.getImplementerId() != null) {
            actorFormatted = userRepository.findById(log.getImplementerId())
                    .map(user -> String.format("%s (%s)", user.getUsername(), user.getRole()))
                    .orElse("User đã bị xóa (ID: " + log.getImplementerId() + ")");
        }

        // 2. Xác định Target và lấy tên cụ thể
        String target = "";
//        if (log.getTargetProject() != null) {
//            // Giả định Entity Project của bạn có hàm getName(), hãy đổi lại cho đúng nếu bạn dùng tên khác
//            target = projectRepository.findById(log.getTargetProject())
//                    .map(project -> project.getName())
//                    .orElse("Project đã xóa (ID: " + log.getTargetProject() + ")");
//
//        } else
        if (log.getTargetUser() != null) {
            target = userRepository.findById(log.getTargetUser())
                    .map(user -> "User: " + user.getUsername())
                    .orElse("User đã xóa (ID: " + log.getTargetUser() + ")");
        }

        // 3. Trả về DTO
        return new AuditLogResponse(
                log.getCreatedAt(),
                actorFormatted,
                log.getActionType() != null ? log.getActionType().name() : "",
                target,
                log.getDescribe()
        );
    }

    @Override
    public void logUserAction(Integer actorId, ActionType action, Integer targetUserId, String description) {
        AuditLog log = AuditLog.builder()
                .implementerId(actorId)
                .actionType(action)
                .targetUser(targetUserId)
                .describe(description)
                .build();
        auditLogRepository.save(log);
    }

    @Override
    public void logProjectAction(Integer actorId, ActionType action, Integer targetProjectId, String description) {
        AuditLog log = AuditLog.builder()
                .implementerId(actorId)
                .actionType(action)
                .targetProject(targetProjectId) // Lưu ID dự án bị tác động
                .describe(description)
                .build();
        auditLogRepository.save(log);
    }
}
