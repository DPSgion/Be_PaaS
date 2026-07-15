package com.be_paas.modules.monitoring.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import com.be_paas.modules.deployment.repository.DeploymentRepository;
import com.be_paas.modules.monitoring.dto.ProjectMetricsResponse;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;

    @Override
    public ProjectMetricsResponse getProjectMetrics(Integer projectId, String username) {
        // 1. Lấy thông tin dự án và kiểm tra quyền sở hữu tuyệt đối
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo bảo mật: User {} cố xem tài nguyên giám sát của Project ID {}", username, projectId);
            throw new BusinessException(403, "Bạn không có quyền truy cập dữ liệu giám sát của dự án này");
        }

        // 2. Lấy Container ID hiện tại đang chạy
        String containerId = project.getContainerId();

        // 3. Lấy dung lượng Image từ lịch sử Deploy thành công gần nhất
        Double imageSizeMb = 0.0;
        Deployment latestSuccessDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElse(null);

        if (latestSuccessDeploy != null && latestSuccessDeploy.getImageSize() != null) {
            // Chia cho 1,000,000 (chuẩn hệ Thập phân) để đồng bộ tuyệt đối với Docker UI
            imageSizeMb = Math.round((latestSuccessDeploy.getImageSize() / 1000000.0) * 100.0) / 100.0;
        }

        // 4. Đóng gói và trả về DTO
        return new ProjectMetricsResponse(containerId, imageSizeMb);
    }
}