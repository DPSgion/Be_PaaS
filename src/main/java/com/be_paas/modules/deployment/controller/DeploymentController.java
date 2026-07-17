package com.be_paas.modules.deployment.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.deployment.dto.DeploymentHistoryResponse;
import com.be_paas.modules.deployment.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping("/{projectId}/deploy")
    public ResponseEntity<String> triggerDeployment(@PathVariable Integer projectId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Phát lệnh chạy ngầm và không chờ đợi
        deploymentService.deployProject(projectId, currentUsername);

        // Trả về HTTP 202 (Accepted) ngay lập tức cho Frontend
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Tiến trình triển khai đang được chạy ngầm. Vui lòng kiểm tra lại trạng thái sau ít phút.");
    }

    @PostMapping("/{projectId}/restart")
    public ResponseEntity<String> restartProject(@PathVariable Integer projectId) {
        // Lấy định danh user từ Security Context
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Gọi luồng xử lý
        deploymentService.restartProject(projectId, currentUsername);

        return ResponseEntity.ok("Dự án đã được khởi động lại thành công.");
    }

    @PostMapping("/{projectId}/stop")
    public ResponseEntity<String> stopProject(@PathVariable Integer projectId) {
        // Lấy định danh user từ Security Context
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Chuyển luồng xử lý xuống tầng Service
        deploymentService.stopProject(projectId, currentUsername);

        return ResponseEntity.ok("Dự án đã được dừng thành công.");
    }

    @PostMapping("/{projectId}/start")
    public ResponseEntity<String> startProject(@PathVariable Integer projectId) {
        // Lấy định danh user từ Security Context
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Chuyển luồng xử lý xuống tầng Service
        deploymentService.startProject(projectId, currentUsername);

        return ResponseEntity.ok("Dự án đã được khởi động thành công.");
    }

    @GetMapping("/project/{projectId}/histories")
    public ResponseEntity<PageResponse<DeploymentHistoryResponse>> getDeployHistories(
            @PathVariable Integer projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        PageResponse<DeploymentHistoryResponse> histories =
                deploymentService.getProjectDeployHistories(projectId, currentUsername, page, size);

        return ResponseEntity.ok(histories);
    }

    @GetMapping(value = "/{deploymentId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getDeploymentLog(@PathVariable Integer deploymentId) {
        // 1. Lấy định danh user từ Security Context
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. Chuyển xuống Service xử lý I/O và bảo mật
        String logContent = deploymentService.getDeploymentLog(deploymentId, currentUsername);

        // 3. Trả về Frontend kèm HTTP Status 200 (OK)
        return ResponseEntity.ok(logContent);
    }

    @GetMapping(value = "/{deploymentId}/logs/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDeploymentLog(@PathVariable Integer deploymentId) {
        // Lấy định danh user
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Mở kết nối SSE
        return deploymentService.streamDeploymentLog(deploymentId, currentUsername);
    }
}