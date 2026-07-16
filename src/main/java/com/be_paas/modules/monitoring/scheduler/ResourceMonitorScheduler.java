package com.be_paas.modules.monitoring.scheduler;

import com.be_paas.modules.deployment.service.DockerService;
import com.be_paas.modules.monitoring.dto.ContainerStatsDTO;
import com.be_paas.modules.monitoring.entity.ResourceLog;
import com.be_paas.modules.monitoring.repository.ResourceLogRepository;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceMonitorScheduler {

    private final ProjectRepository projectRepository;
    private final DockerService dockerService;
    private final ResourceLogRepository resourceLogRepository;

    /**
     * Luồng 1: Thu thập dữ liệu
     * Chạy lặp lại mỗi 30 giây (30000 milliseconds)
     */
    @Scheduled(fixedRate = 30000)
    public void collectResourceStats() {
        // Chỉ quét những dự án đang RUNNING
        List<Project> runningProjects = projectRepository.findByStatus(ProjectStatus.RUNNING);

        for (Project project : runningProjects) {
            String containerId = project.getContainerId();
            if (containerId == null || containerId.trim().isEmpty()) {
                continue;
            }

            try {
                // Gọi sang DockerService để lấy chỉ số snapshot
                ContainerStatsDTO stats = dockerService.getContainerStats(containerId);

                // Tạo bản ghi mới và lưu xuống DB
                ResourceLog logRecord = new ResourceLog();
                logRecord.setProject(project);
                logRecord.setCpuUsage(stats.cpuUsage());
                logRecord.setRamUsage(stats.ramUsage());

                resourceLogRepository.save(logRecord);

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