package com.be_paas.modules.monitoring.scheduler;

import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import com.be_paas.modules.deployment.repository.DeploymentRepository;
import com.be_paas.modules.deployment.service.DockerService;
import com.be_paas.modules.monitoring.dto.ContainerStatsDTO;
import com.be_paas.modules.monitoring.dto.ResourceChartResponse;
import com.be_paas.modules.monitoring.entity.ResourceLog;
import com.be_paas.modules.monitoring.repository.ResourceLogRepository;
import com.be_paas.modules.monitoring.service.MonitoringService;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceMonitorScheduler {

    private final ProjectRepository projectRepository;
    private final DockerService dockerService;
    private final ResourceLogRepository resourceLogRepository;
    private final DeploymentRepository deploymentRepository;
    private final MonitoringService monitoringService;

    /**
     * Luồng 1: Thu thập dữ liệu
     * Chạy lặp lại mỗi 30 giây (30000 milliseconds)
     */
    @Scheduled(fixedRate = 30000)
    public void collectResourceStats() {
        // Chỉ quét những dự án đang RUNNING
        List<Project> runningProjects = projectRepository.findByStatus(ProjectStatus.RUNNING);

        for (Project project : runningProjects) {
            try {
                // SỬA GẮT 2: Truy tìm Container ID từ lịch sử Deploy thành công gần nhất
                Optional<Deployment> latestDeploy = deploymentRepository
                        .findFirstByProjectIdAndStatusOrderByIdDesc(project.getId(), DeploymentStatus.SUCCESS);

                // Nếu không có lịch sử deploy hoặc containerId trống thì bỏ qua
                if (latestDeploy.isEmpty() ||
                        latestDeploy.get().getContainerId() == null ||
                        latestDeploy.get().getContainerId().trim().isEmpty()) {
                    continue;
                }

                String containerId = latestDeploy.get().getContainerId();

                // Gọi sang DockerService để lấy chỉ số snapshot
                ContainerStatsDTO stats = dockerService.getContainerStats(containerId);

                // Tạo bản ghi mới và lưu xuống DB
                ResourceLog logRecord = new ResourceLog();
                logRecord.setProject(project);
                logRecord.setCpuUsage(stats.cpuUsage());
                logRecord.setRamUsage(stats.ramUsage());

                resourceLogRepository.save(logRecord);

                // Đóng gói dữ liệu để phát đi
                ResourceChartResponse liveData = new ResourceChartResponse(
                        logRecord.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        logRecord.getCpuUsage(),
                        logRecord.getRamUsage()
                );

                // BƠM VÀO ỐNG SSE ĐANG CHỜ
                monitoringService.sendChartData(project.getId(), liveData);

            } catch (Exception e) {
                log.error("⚠️ Không thể thu thập tài nguyên cho Project ID {}: {}", project.getId(), e.getMessage());
            }
        }
    }

    /**
     * Luồng 2: Dọn dẹp dữ liệu cũ (Cuốn chiếu)
     * Chạy vào lúc 02:00:00 sáng mỗi ngày
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional // Bắt buộc có @Transactional khi thực thi câu lệnh @Modifying DELETE
    public void cleanupOldResourceLogs() {
        log.info("🧹 Bắt đầu tiến trình dọn dẹp nhật ký tài nguyên cũ...");
        try {
            // Xác định mốc thời gian: Trừ đi 24 giờ so với hiện tại
            LocalDateTime threshold = LocalDateTime.now().minusHours(24);

            resourceLogRepository.deleteOlderThan(threshold);
            log.info("✅ Đã hoàn tất dọn dẹp các bản ghi tài nguyên trước {}", threshold);
        } catch (Exception e) {
            log.error("❌ Lỗi khi dọn dẹp nhật ký tài nguyên: {}", e.getMessage());
        }
    }
}