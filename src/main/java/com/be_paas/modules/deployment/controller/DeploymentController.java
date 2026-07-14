package com.be_paas.modules.deployment.controller;

import com.be_paas.modules.deployment.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
}