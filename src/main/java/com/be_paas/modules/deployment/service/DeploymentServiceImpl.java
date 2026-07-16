package com.be_paas.modules.deployment.service;

import com.be_paas.core.config.AESUtil;
import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.deployment.dto.WorkspaceResult;
import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import com.be_paas.modules.deployment.repository.DeploymentRepository;
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
    private final DeploymentRepository deploymentRepository;

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
        Deployment deployment = null;

        try {
            log.info("⏳ [Project {}] Đang xác thực thông tin dự án...", projectId);

            // 1. Xác thực quyền sở hữu
            project = projectRepository.findByIdWithUser(projectId)
                    .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

            if (!project.getUser().getUsername().equals(username)) {
                log.warn("❌ [Project {}] Truy cập trái phép bởi User {}", projectId, username);
                return CompletableFuture.completedFuture(null);
            }

            String patToken = project.getUser().getGithubAccessToken();
            if (patToken == null || patToken.isEmpty()) {
                log.error("❌ [Project {}] Lỗi: User chưa liên kết GitHub", projectId);
                updateProjectStatus(project, ProjectStatus.CRASHED, null);
                return CompletableFuture.completedFuture(null);
            }

            // 2. Báo hiệu trạng thái Building cho Project
            updateProjectStatus(project, ProjectStatus.BUILDING, null);

            // ========================================================
            // [MỞ SỔ LỊCH SỬ DEPLOYMENT]
            // ========================================================
            deployment = new Deployment();
            deployment.setProjectId(projectId);
            deployment.setStatus(DeploymentStatus.BUILDING);
            deployment.setStartTime(LocalDateTime.now());

            deployment = deploymentRepository.save(deployment);

            String logFilePath = "/var/bepaas/logs/deploy_" + deployment.getId() + ".log";
            deployment.setFilePath(logFilePath);

            deploymentRepository.save(deployment);
            log.info("📝 [Project {}] Đã khởi tạo hồ sơ Deploy ID: {}", projectId, deployment.getId());
            // ========================================================

            // 3. Khởi tạo Workspace & Clone code
            log.info("📦 [Project {}] BƯỚC 1/4: Đang kéo mã nguồn từ GitHub (Branch: {})...", projectId, project.getBranch());

            WorkspaceResult workspaceResult = workspaceService.cloneRepository(
                    project.getId(),
                    project.getGithubUrl(),
                    project.getBranch(),
                    patToken
            );

            Path workspacePath = workspaceResult.workspacePath();

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
                internalPort = portManagerService.allocateAvailablePort();
                log.info("🔌 [Project {}] Cấp phát port mới: {}", projectId, internalPort);
            } else {
                log.info("🔌 [Project {}] Tái sử dụng port cũ: {}", projectId, internalPort);
            }

            // 6. Đóng gói & Chạy Docker
            log.info("🐳 [Project {}] BƯỚC 4/4: Đang gửi lệnh cho Docker...", projectId);
            String imageName = imagePrefix + project.getId();
            String containerName = containerPrefix + project.getId();

            log.info("🧹 [Project {}] Đang dọn dẹp Container và Image cũ (nếu có)...", projectId);
            dockerService.cleanupContainerAndImage(containerName, imageName);

            Integer targetPort = project.getTargetPort() != null ? project.getTargetPort() : 80;

            String imageId = dockerService.buildImage(buildContextPath, imageName);
            log.info("✅ [Project {}] Đã build xong Image ID: {}", projectId, imageId);

            Long imageSize = dockerService.getImageSize(imageName);
            deployment.setImageSize(imageSize);
            log.info("📊 [Project {}] Dung lượng Image (Tag {}): {} Bytes", projectId, imageName, imageSize);

            String containerId = dockerService.runContainer(imageId, containerName, internalPort, targetPort, buildContextPath);

            // SỬA GẮT: Gán containerId cho Deployment thay vì Project
            deployment.setContainerId(containerId);
            log.info("✅ [Project {}] Đã chạy thành công Container ID: {}", projectId, containerId);

            // 7. Hoàn tất (Cập nhật trạng thái Project hiện tại)
            updateProjectStatus(project, ProjectStatus.RUNNING, internalPort);

            // ========================================================
            // [CHỐT SỔ LỊCH SỬ THÀNH CÔNG]
            // ========================================================
            deployment.setCommitSha(workspaceResult.commitSha());
            deployment.setCommitMessage(workspaceResult.commitMessage());
            deployment.setCommitter(workspaceResult.committer());
            deployment.setStatus(DeploymentStatus.SUCCESS);
            deployment.setEndTime(LocalDateTime.now());

            deploymentRepository.save(deployment);
            // ========================================================

            log.info("🎉 [Project {}] HOÀN TẤT DEPLOY! Ứng dụng đang chạy ở Port: {}", projectId, internalPort);
            log.info("=====================================================");

        } catch (Exception e) {
            log.error("🔥 [Project {}] LỖI CHÍ MẠNG KHI DEPLOY: {}", projectId, e.getMessage(), e);

            if (project != null) {
                updateProjectStatus(project, ProjectStatus.CRASHED, null);
            }

            if (deployment != null) {
                deployment.setStatus(DeploymentStatus.FAILED);
                deployment.setEndTime(LocalDateTime.now());
                deploymentRepository.save(deployment);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void restartProject(Integer projectId, String username) {
        log.info("User {} yêu cầu Restart Project ID: {}", username, projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo xâm nhập: User {} cố tình Restart Project {} của User {}",
                    username, projectId, project.getUser().getUsername());
            throw new BusinessException(403, "Bạn không có quyền thao tác trên dự án này");
        }

        // SỬA GẮT: Tìm ID từ bản Deploy SUCCESS gần nhất
        Deployment latestDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElseThrow(() -> new BusinessException(400, "Dự án chưa được khởi tạo môi trường (chưa Deploy lần nào) nên không thể Restart."));

        String containerId = latestDeploy.getContainerId();
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new BusinessException(400, "Không tìm thấy Container ID hợp lệ để khởi động lại.");
        }

        dockerService.restartContainer(containerId);

        if (project.getStatus() != ProjectStatus.RUNNING) {
            project.setStatus(ProjectStatus.RUNNING);
            projectRepository.save(project);
            log.info("Đã cập nhật trạng thái Project {} thành RUNNING", projectId);
        }
    }

    @Override
    public void stopProject(Integer projectId, String username) {
        log.info("User {} yêu cầu Stop Project ID: {}", username, projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo xâm nhập: User {} cố tình Stop Project {} của User {}",
                    username, projectId, project.getUser().getUsername());
            throw new BusinessException(403, "Bạn không có quyền thao tác trên dự án này");
        }

        // SỬA GẮT: Tìm ID từ bản Deploy SUCCESS gần nhất
        Deployment latestDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElseThrow(() -> new BusinessException(400, "Dự án chưa được khởi tạo môi trường (chưa Deploy lần nào) nên không thể dừng."));

        String containerId = latestDeploy.getContainerId();
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new BusinessException(400, "Không tìm thấy Container ID hợp lệ để dừng.");
        }

        dockerService.stopContainer(containerId);

        if (project.getStatus() != ProjectStatus.STOPPED) {
            project.setStatus(ProjectStatus.STOPPED);
            projectRepository.save(project);
            log.info("Đã cập nhật trạng thái Project {} thành STOPPED", projectId);
        }
    }

    @Override
    public void startProject(Integer projectId, String username) {
        log.info("User {} yêu cầu Start Project ID: {}", username, projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo xâm nhập: User {} cố tình Start Project {} của User {}",
                    username, projectId, project.getUser().getUsername());
            throw new BusinessException(403, "Bạn không có quyền thao tác trên dự án này");
        }

        // SỬA GẮT: Tìm ID từ bản Deploy SUCCESS gần nhất
        Deployment latestDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElseThrow(() -> new BusinessException(400, "Dự án chưa được khởi tạo môi trường (chưa Deploy lần nào) nên không thể khởi động."));

        String containerId = latestDeploy.getContainerId();
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new BusinessException(400, "Không tìm thấy Container ID hợp lệ để khởi động.");
        }

        dockerService.startContainer(containerId);

        if (project.getStatus() != ProjectStatus.RUNNING) {
            project.setStatus(ProjectStatus.RUNNING);
            projectRepository.save(project);
            log.info("Đã cập nhật trạng thái Project {} thành RUNNING", projectId);
        }
    }

    /**
     * Hàm phụ trợ lưu trạng thái vào DB
     */
    private void updateProjectStatus(Project project, ProjectStatus status, Integer port) {
        project.setStatus(status);
        project.setLastHealthCheck(LocalDateTime.now());
        if (port != null) project.setInternalPort(port);
        projectRepository.save(project);
    }
}