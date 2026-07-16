package com.be_paas.modules.monitoring.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import com.be_paas.modules.deployment.repository.DeploymentRepository;
import com.be_paas.modules.monitoring.dto.ProjectMetricsResponse;
import com.be_paas.modules.monitoring.dto.ResourceChartResponse;
import com.be_paas.modules.monitoring.entity.ResourceLog;
import com.be_paas.modules.monitoring.repository.ResourceLogRepository;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final ResourceLogRepository resourceLogRepository;

    private final Map<Integer, CopyOnWriteArrayList<SseEmitter>> projectEmitters = new ConcurrentHashMap<>();

    @Override
    public ProjectMetricsResponse getProjectMetrics(Integer projectId, String username) {
        // 1. Lấy thông tin dự án và kiểm tra quyền sở hữu tuyệt đối
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo bảo mật: User {} cố xem tài nguyên giám sát của Project ID {}", username, projectId);
            throw new BusinessException(403, "Bạn không có quyền truy cập dữ liệu giám sát của dự án này");
        }

        // 2 & 3. Lấy Container ID và dung lượng Image cùng lúc từ lịch sử Deploy thành công gần nhất
        String containerId = null;
        Double imageSizeMb = 0.0;

        Deployment latestSuccessDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElse(null);

        if (latestSuccessDeploy != null) {
            containerId = latestSuccessDeploy.getContainerId(); // Bóc ID từ bảng trien_khai

            if (latestSuccessDeploy.getImageSize() != null) {
                // Chia cho 1,000,000 (chuẩn hệ Thập phân) để đồng bộ tuyệt đối với Docker UI
                imageSizeMb = Math.round((latestSuccessDeploy.getImageSize() / 1000000.0) * 100.0) / 100.0;
            }
        }

        // 4. Đóng gói và trả về DTO
        return new ProjectMetricsResponse(containerId, imageSizeMb);
    }

    @Override
    public List<ResourceChartResponse> getResourceChart(Integer projectId, String username) {
        // 1. Kiểm tra quyền sở hữu dự án (Tương tự hàm lấy thông số tĩnh)
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo bảo mật: User {} cố xem biểu đồ của Project ID {}", username, projectId);
            throw new BusinessException(403, "Bạn không có quyền truy cập dữ liệu giám sát của dự án này");
        }

        // 2. Lấy danh sách lịch sử tài nguyên (đã sắp xếp cũ -> mới)
        List<ResourceLog> logs = resourceLogRepository.findByProjectIdOrderByCreatedAtAsc(projectId);

        // 3. Định dạng thời gian (Chỉ lấy Giờ:Phút:Giây cho biểu đồ)
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // 4. Gom trực tiếp thành 1 mảng các object
        List<ResourceChartResponse> chartData = logs.stream()
                .map(log -> new ResourceChartResponse(
                        log.getCreatedAt().format(timeFormatter),
                        log.getCpuUsage(),
                        log.getRamUsage()
                ))
                .toList();

        // 5. Trả về DTO chuẩn
        return chartData;
    }

    @Override
    public SseEmitter subscribe(Integer projectId, String username) {
        // Kiểm tra quyền sở hữu dự án trước khi cho phép mở luồng stream
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            throw new BusinessException(403, "Bạn không có quyền xem luồng dữ liệu của dự án này");
        }

        // Khởi tạo ống dẫn sống trong 30 phút
        SseEmitter emitter = new SseEmitter(1800000L);

        // Nhét ống dẫn vào đúng hộc tủ của ProjectId
        projectEmitters.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Dọn dẹp bộ nhớ khi kết nối đứt
        emitter.onCompletion(() -> removeEmitter(projectId, emitter));
        emitter.onTimeout(() -> removeEmitter(projectId, emitter));
        emitter.onError((e) -> removeEmitter(projectId, emitter));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected Realtime Chart for Project " + projectId));
        } catch (IOException e) {
            removeEmitter(projectId, emitter);
        }

        return emitter;
    }

    @Override
    public void sendChartData(Integer projectId, ResourceChartResponse data) {
        CopyOnWriteArrayList<SseEmitter> emitters = projectEmitters.get(projectId);

        if (emitters != null && !emitters.isEmpty()) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("NEW_CHART_DATA").data(data));
                } catch (IOException e) {
                    emitter.complete();
                    emitters.remove(emitter);
                }
            }
            // Dọn luôn mảng nếu không còn ai nghe
            if (emitters.isEmpty()) {
                projectEmitters.remove(projectId);
            }
        }
    }

    // Hàm phụ trợ dọn dẹp Emitter
    private void removeEmitter(Integer projectId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = projectEmitters.get(projectId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                projectEmitters.remove(projectId);
            }
        }
    }
}