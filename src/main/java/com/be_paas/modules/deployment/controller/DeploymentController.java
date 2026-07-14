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
}