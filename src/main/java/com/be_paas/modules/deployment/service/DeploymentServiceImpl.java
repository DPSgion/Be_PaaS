package com.be_paas.modules.deployment.service;

import com.be_paas.core.config.AESUtil;
import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.project.entity.EnvironmentVariable;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.repository.EnvironmentVariableRepository;
import com.be_paas.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {
    @Value("${app.bepaas.docker.image-prefix}")
    private String imagePrefix;

    @Value("${app.bepaas.docker.container-prefix}")
    private String containerPrefix;


    private final ProjectRepository projectRepository;
    private final EnvironmentVariableRepository envVarRepository;

    private final WorkspaceService workspaceService;
    private final PortManagerService portManagerService;
    private final DockerService dockerService;
    private final AESUtil aesUtil;

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> deployProject(Integer projectId, String username) {
        log.info("=====================================================");
        log.info("🚀 [Project {}] BẮT ĐẦU TIẾN TRÌNH DEPLOY NGẦM", projectId);

        Project project = null;

        // BAO TRỌN TOÀN BỘ CODE BẰNG TRY-CATCH
        try {
            log.info("⏳ [Project {}] Đang xác thực thông tin dự án...", projectId);

            // 1. Xác thực quyền sở hữu (Sử dụng hàm hàm JOIN FETCH vừa tạo)
            project = projectRepository.findByIdWithUser(projectId)
                    .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

            if (!project.getUser().getUsername().equals(username)) {
                log.warn("❌ [Project {}] Truy cập trái phép bởi User {}", projectId, username);
                return CompletableFuture.completedFuture(null);
            }

            String patToken = project.getUser().getGithubAccessToken();
            if (patToken == null || patToken.isEmpty()) {
                log.error("❌ [Project {}] Lỗi: User chưa liên kết GitHub", projectId);
                updateProjectStatus(project, ProjectStatus.CRASHED, null, null);
                return CompletableFuture.completedFuture(null);
            }

            // 2. Báo hiệu trạng thái Building
            updateProjectStatus(project, ProjectStatus.BUILDING, null, null);

            // 3. Khởi tạo Workspace & Clone code
            log.info("📦 [Project {}] BƯỚC 1/4: Đang kéo mã nguồn từ GitHub (Branch: {})...", projectId, project.getBranch());
            Path workspacePath = workspaceService.cloneRepository(
                    project.getId(),
                    project.getGithubUrl(),
                    project.getBranch(),
                    patToken
            );

            // Xử lý thư mục gốc (Root Directory)
            String subDir = project.getRootDirectory();
            Path buildContextPath = (subDir != null && !subDir.trim().isEmpty())
                    ? workspacePath.resolve(subDir)
                    : workspacePath;

            // 4. Tiêm Biến môi trường
            log.info("⚙️ [Project {}] BƯỚC 2/4: Đang tiêm biến môi trường (.env)...", projectId);
            List<EnvironmentVariable> envVars = envVarRepository.findByProjectId(projectId);
            Map<String, String> envMap = envVars.stream()
                    .collect(Collectors.toMap(
                            EnvironmentVariable::getKeyName,
                            env -> aesUtil.decrypt(env.getValue())
                    ));
            workspaceService.generateEnvFile(buildContextPath, envMap);

            // 5. Cấp phát Cổng động
            log.info("🔌 [Project {}] BƯỚC 3/4: Đang kiểm tra cổng mạng nội bộ...", projectId);
            Integer internalPort = project.getInternalPort();

            if (internalPort == null) {
                // Chỉ cấp mới khi deploy lần đầu
                internalPort = portManagerService.allocateAvailablePort();
                log.info("🔌 [Project {}] Cấp phát port mới: {}", projectId, internalPort);
            } else {
                // Dùng lại port cũ cho Redeploy
                log.info("🔌 [Project {}] Tái sử dụng port cũ: {}", projectId, internalPort);
            }

            // 6. Đóng gói & Chạy Docker
            log.info("🐳 [Project {}] BƯỚC 4/4: Đang gửi lệnh cho Docker...", projectId);
            String imageName = imagePrefix + project.getId();
            String containerName = containerPrefix + project.getId();

            // [SỬA GẮT: CHÈN LOGIC DỌN DẸP Ở ĐÂY]
            log.info("🧹 [Project {}] Đang dọn dẹp Container và Image cũ (nếu có)...", projectId);
            dockerService.cleanupContainerAndImage(containerName, imageName);

            // Lấy Target Port từ DB, nếu null thì fallback về 80
            Integer targetPort = project.getTargetPort() != null ? project.getTargetPort() : 80;

            String imageId = dockerService.buildImage(buildContextPath, imageName);
            log.info("✅ [Project {}] Đã build xong Image ID: {}", projectId, imageId);

            String containerId = dockerService.runContainer(imageId, containerName, internalPort, targetPort, buildContextPath);
            log.info("✅ [Project {}] Đã chạy thành công Container ID: {}", projectId, containerId);

            // 7. Hoàn tất
            updateProjectStatus(project, ProjectStatus.RUNNING, containerId, internalPort);
            log.info("🎉 [Project {}] HOÀN TẤT DEPLOY! Ứng dụng đang chạy ở Port: {}", projectId, internalPort);
            log.info("=====================================================");

        } catch (Exception e) {
            log.error("🔥 [Project {}] LỖI CHÍ MẠNG KHI DEPLOY: {}", projectId, e.getMessage(), e); // In ra Stacktrace để bắt lỗi
            if (project != null) {
                updateProjectStatus(project, ProjectStatus.CRASHED, null, null);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Hàm phụ trợ lưu trạng thái vào DB.
     * Lưu độc lập để không giữ Transaction kéo dài.
     */
    private void updateProjectStatus(Project project, ProjectStatus status, String containerId, Integer port) {
        project.setStatus(status);
        project.setLastHealthCheck(LocalDateTime.now());
        if (containerId != null) project.setContainerId(containerId);
        if (port != null) project.setInternalPort(port);
        projectRepository.save(project);
    }
}