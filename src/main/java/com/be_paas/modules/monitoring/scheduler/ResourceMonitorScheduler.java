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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        // 1. Quét danh sách dự án (Nhanh, giải phóng connection ngay)
        List<Project> runningProjects = projectRepository.findByStatus(ProjectStatus.RUNNING);

        if (runningProjects.isEmpty()) {
            return;
        }

        // 2. CHẠY SONG SONG (Parallel Stream) để không bị kẹt 5 giây/dự án
        List<ResourceLog> logsToSave = runningProjects.parallelStream().map(project -> {
            try {
                // Lấy ID từ lịch sử Deploy (DB I/O nhỏ)
                Optional<Deployment> latestDeploy = deploymentRepository
                        .findFirstByProjectIdAndStatusOrderByIdDesc(project.getId(), DeploymentStatus.SUCCESS);

                if (latestDeploy.isEmpty() || latestDeploy.get().getContainerId() == null || latestDeploy.get().getContainerId().trim().isEmpty()) {
                    return null; // Bỏ qua nếu không có container
                }

                String containerId = latestDeploy.get().getContainerId();

                // GỌI DOCKER (Mất 5 giây nhưng các dự án đang chạy song song nên không lo kẹt)
                ContainerStatsDTO stats = dockerService.getContainerStats(containerId);

                // Khởi tạo bản ghi
                ResourceLog logRecord = new ResourceLog();
                logRecord.setProject(project);
                logRecord.setCpuUsage(stats.cpuUsage());
                logRecord.setRamUsage(stats.ramUsage());

                // Đóng gói và bơm vào ống SSE ngay lập tức cho Frontend thấy độ trễ thấp nhất
                ResourceChartResponse liveData = new ResourceChartResponse(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        stats.cpuUsage(),
                        stats.ramUsage()
                );
                monitoringService.sendChartData(project.getId(), liveData);

                return logRecord; // Trả về đối tượng để gom vào List

            } catch (Exception e) {
                log.error("⚠️ Không thể thu thập tài nguyên cho Project ID {}: {}", project.getId(), e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList()); // Lọc bỏ các dự án lỗi (null)

        // 3. LƯU BATCH (Lưu 1 cục vào DB)
        // Giải phóng áp lực hoàn toàn cho Hikari Connection Pool vì chỉ gọi DB đúng 1 lần
        if (!logsToSave.isEmpty()) {
            resourceLogRepository.saveAll(logsToSave);
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