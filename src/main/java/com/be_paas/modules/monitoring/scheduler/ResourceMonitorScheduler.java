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
import com.be_paas.modules.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ResourceMonitorScheduler implements SchedulingConfigurer { // SỬA GẮT: Đã thêm interface

    private final ProjectRepository projectRepository;
    private final DockerService dockerService;
    private final ResourceLogRepository resourceLogRepository;
    private final DeploymentRepository deploymentRepository;
    private final MonitoringService monitoringService;
    private final SystemSettingService systemSettingService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {

        // LUỒNG 1: QUÉT TÀI NGUYÊN (Tính bằng mili-giây)
        taskRegistrar.addTriggerTask(
                this::collectResourceStats,
                triggerContext -> {
                    String rateStr = systemSettingService.getValue("MONITOR_CRON_RATE_MS");
                    long rate = rateStr.isEmpty() ? 30000 : Long.parseLong(rateStr);

                    log.debug("Luồng giám sát đang chạy với chu kỳ {} ms", rate);

                    // SỬA GẮT: Trả về trực tiếp kiểu Instant cho Spring Boot 3
                    return Instant.now().plusMillis(rate);
                }
        );

        // LUỒNG 2: DỌN RÁC NHẬT KÝ (Tính bằng Cron Expression)
        taskRegistrar.addTriggerTask(
                this::cleanupOldResourceLogs,
                triggerContext -> {
                    String cronExpr = systemSettingService.getValue("CLEANUP_LOG_CRON");
                    if (cronExpr.isEmpty()) {
                        cronExpr = "0 0 2 * * ?";
                    }

                    CronTrigger trigger = new CronTrigger(cronExpr);
                    // Lệnh này mặc định trả về Instant trên Spring Boot 3
                    return trigger.nextExecution(triggerContext);
                }
        );
    }

    /**
     * Luồng 1: Thu thập dữ liệu
     * SỬA GẮT: Đã xóa sổ @Scheduled tĩnh
     */
    public void collectResourceStats() {
        List<Project> runningProjects = projectRepository.findByStatus(ProjectStatus.RUNNING);

        if (runningProjects.isEmpty()) {
            return;
        }

        List<ResourceLog> logsToSave = runningProjects.parallelStream().map(project -> {
            try {
                Optional<Deployment> latestDeploy = deploymentRepository
                        .findFirstByProjectIdAndStatusOrderByIdDesc(project.getId(), DeploymentStatus.SUCCESS);

                if (latestDeploy.isEmpty() || latestDeploy.get().getContainerId() == null || latestDeploy.get().getContainerId().trim().isEmpty()) {
                    return null;
                }

                String containerId = latestDeploy.get().getContainerId();

                ContainerStatsDTO stats = dockerService.getContainerStats(containerId);

                ResourceLog logRecord = new ResourceLog();
                logRecord.setProject(project);
                logRecord.setCpuUsage(stats.cpuUsage());
                logRecord.setRamUsage(stats.ramUsage());

                ResourceChartResponse liveData = new ResourceChartResponse(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        stats.cpuUsage(),
                        stats.ramUsage()
                );
                monitoringService.sendChartData(project.getId(), liveData);

                return logRecord;

            } catch (Exception e) {
                log.error("⚠️ Không thể thu thập tài nguyên cho Project ID {}: {}", project.getId(), e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if (!logsToSave.isEmpty()) {
            resourceLogRepository.saveAll(logsToSave);
        }
    }

    /**
     * Luồng 2: Dọn dẹp dữ liệu cũ (Cuốn chiếu)
     * SỬA GẮT: Đã xóa sổ @Scheduled tĩnh, chỉ giữ lại @Transactional
     */
    @Transactional
    public void cleanupOldResourceLogs() {
        log.info("🧹 Bắt đầu tiến trình dọn dẹp nhật ký tài nguyên cũ...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusHours(24);

            resourceLogRepository.deleteOlderThan(threshold);
            log.info("✅ Đã hoàn tất dọn dẹp các bản ghi tài nguyên trước {}", threshold);
        } catch (Exception e) {
            log.error("❌ Lỗi khi dọn dẹp nhật ký tài nguyên: {}", e.getMessage());
        }
    }
}