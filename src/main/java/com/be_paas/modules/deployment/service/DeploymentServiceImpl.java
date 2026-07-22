package com.be_paas.modules.deployment.service;

import com.be_paas.core.config.AESUtil;
import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.auditlog.entity.ActionType;
import com.be_paas.modules.auditlog.service.AuditLogService;
import com.be_paas.modules.deployment.dto.DeploymentHistoryResponse;
import com.be_paas.modules.deployment.dto.WorkspaceResult;
import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import com.be_paas.modules.deployment.repository.DeploymentRepository;
import com.be_paas.modules.mail.service.MailService;
import com.be_paas.modules.notification.entity.NotificationType;
import com.be_paas.modules.notification.service.NotificationService;
import com.be_paas.modules.project.entity.EnvironmentVariable;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.repository.EnvironmentVariableRepository;
import com.be_paas.modules.project.repository.ProjectRepository;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final NotificationService notificationService;

    private final WorkspaceService workspaceService;
    private final PortManagerService portManagerService;
    private final DockerService dockerService;
    private final AESUtil aesUtil;


    @Value("${app.deployment.log-dir}")
    private String logDirectory;

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> deployProject(Integer projectId, String username) {
        log.info("=====================================================");
        log.info("🚀 [Project {}] BẮT ĐẦU TIẾN TRÌNH DEPLOY NGẦM", projectId);

        Project project = null;
        Deployment deployment = null;
        String logFilePath = null; // Khai báo ra ngoài để catch block có thể dùng được

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
            // [MỞ SỔ LỊCH SỬ DEPLOYMENT & KHỞI TẠO FILE LOG]
            // ========================================================
            deployment = new Deployment();
            deployment.setProjectId(projectId);
            deployment.setStatus(DeploymentStatus.BUILDING);
            deployment.setStartTime(LocalDateTime.now());
            deployment = deploymentRepository.save(deployment);

            logFilePath = logDirectory + "/deploy_" + deployment.getId() + ".log";
            deployment.setFilePath(logFilePath);
            deploymentRepository.save(deployment);

            writeDeployLog(logFilePath, "🚀 STARTING DEPLOYMENT PROCESS FOR PROJECT ID: " + project.getId());
            log.info("📝 [Project {}] Đã khởi tạo hồ sơ Deploy ID: {}", projectId, deployment.getId());
            // ========================================================

            // 3. Khởi tạo Workspace & Clone code
            log.info("📦 [Project {}] BƯỚC 1/4: Đang kéo mã nguồn từ GitHub (Branch: {})...", projectId, project.getBranch());
            writeDeployLog(logFilePath, "⏳ Connecting to GitHub to clone repository (Branch: " + project.getBranch() + ")...");

            WorkspaceResult workspaceResult = workspaceService.cloneRepository(
                    project.getId(),
                    project.getGithubUrl(),
                    project.getBranch(),
                    patToken
            );

            writeDeployLog(logFilePath, "✅ Source code cloned successfully. Target Commit: " + workspaceResult.commitSha());
            Path workspacePath = workspaceResult.workspacePath();

            String subDir = project.getRootDirectory();
            Path buildContextPath = (subDir != null && !subDir.trim().isEmpty())
                    ? workspacePath.resolve(subDir)
                    : workspacePath;

            // 4. Tiêm Biến môi trường
            log.info("⚙️ [Project {}] BƯỚC 2/4: Đang tiêm biến môi trường (.env)...", projectId);
            writeDeployLog(logFilePath, "⚙️ Reading configuration and injecting environment variables (.env)...");

            List<EnvironmentVariable> envVars = envVarRepository.findByProjectId(projectId);
            Map<String, String> envMap = envVars.stream()
                    .collect(Collectors.toMap(
                            EnvironmentVariable::getKeyName,
                            env -> aesUtil.decrypt(env.getValue())
                    ));
            workspaceService.generateEnvFile(buildContextPath, envMap);
            writeDeployLog(logFilePath, "✅ Environment setup completed (" + envMap.size() + " variables injected).");

            // 5. Cấp phát Cổng động
            log.info("🔌 [Project {}] BƯỚC 3/4: Đang kiểm tra cổng mạng nội bộ...", projectId);
            writeDeployLog(logFilePath, "🔌 Allocating internal network port...");

            Integer internalPort = project.getInternalPort();
            if (internalPort == null) {
                internalPort = portManagerService.allocateAvailablePort();
                log.info("🔌 [Project {}] Cấp phát port mới: {}", projectId, internalPort);
                writeDeployLog(logFilePath, "🔌 Allocated new port: " + internalPort);
            } else {
                log.info("🔌 [Project {}] Tái sử dụng port cũ: {}", projectId, internalPort);
                writeDeployLog(logFilePath, "🔌 Reusing existing port: " + internalPort);
            }

            // 6. Đóng gói & Chạy Docker
            log.info("🐳 [Project {}] BƯỚC 4/4: Đang gửi lệnh cho Docker...", projectId);
            String imageName = imagePrefix + project.getId();
            String containerName = containerPrefix + project.getId();

            log.info("🧹 [Project {}] Đang dọn dẹp Container và Image cũ (nếu có)...", projectId);
            writeDeployLog(logFilePath, "🧹 Cleaning up old containers and images to prevent conflicts...");
            dockerService.cleanupContainerAndImage(containerName, imageName);

            Integer targetPort = project.getTargetPort() != null ? project.getTargetPort() : 80;

            writeDeployLog(logFilePath, "🐳 Executing Docker Build. This process may take a few minutes...");
            String imageId = dockerService.buildImage(buildContextPath, imageName);
            log.info("✅ [Project {}] Đã build xong Image ID: {}", projectId, imageId);
            writeDeployLog(logFilePath, "✅ Docker Build successful. Generated Image ID: " + imageId);

            Long imageSize = dockerService.getImageSize(imageName);
            deployment.setImageSize(imageSize);
            log.info("📊 [Project {}] Dung lượng Image (Tag {}): {} Bytes", projectId, imageName, imageSize);
            writeDeployLog(logFilePath, "📊 Optimized Image Size: " + (imageSize / 1024 / 1024) + " MB");

            writeDeployLog(logFilePath, "🚀 Starting Docker Container...");
            String containerId = dockerService.runContainer(imageId, containerName, internalPort, targetPort, buildContextPath);

            deployment.setContainerId(containerId);
            log.info("✅ [Project {}] Đã chạy thành công Container ID: {}", projectId, containerId);
            writeDeployLog(logFilePath, "✅ Container started successfully. Container ID: " + containerId);

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

            writeDeployLog(logFilePath, "🎉 DEPLOYMENT COMPLETED SUCCESSFULLY! Application is running on Port: " + internalPort);
            writeDeployLog(logFilePath, "[SYSTEM_CODE:DEPLOYMENT_SUCCESS]");
            // ========================================================

            sendDeploymentNotification(project, "THÀNH CÔNG", "Ứng dụng của bạn đã được build và khởi chạy thành công. Hiện đang hoạt động tại Port nội bộ: " + internalPort + ": http://localhost:" + internalPort);

            log.info("🎉 [Project {}] HOÀN TẤT DEPLOY! Ứng dụng đang chạy ở Port: {}", projectId, internalPort);
            log.info("=====================================================");

        } catch (Exception e) {
            log.error("🔥 [Project {}] LỖI CHÍ MẠNG KHI DEPLOY: {}", projectId, e.getMessage(), e);

            // Ghi thẳng lỗi chí mạng vào file log cho người dùng nắm bắt
            if (logFilePath != null) {
                writeDeployLog(logFilePath, "❌ CRITICAL ERROR DURING DEPLOYMENT: " + e.getMessage());
                writeDeployLog(logFilePath, "🛑 Deployment process aborted.");
                writeDeployLog(logFilePath, "[SYSTEM_CODE:DEPLOYMENT_FAILED]");
            }

            if (project != null) {
                updateProjectStatus(project, ProjectStatus.CRASHED, null);
                sendDeploymentNotification(project, "THẤT BẠI", "Quá trình triển khai gặp sự cố: " + e.getMessage() + ". Vui lòng kiểm tra lại mã nguồn, cấu hình Dockerfile hoặc Terminal Log.");
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

        Project project = projectRepository.findByIdWithUser(projectId)
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

        Project project = projectRepository.findByIdWithUser(projectId)
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

        Project project = projectRepository.findByIdWithUser(projectId)
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

    @Override
    public SseEmitter streamDeploymentLog(Integer deploymentId, String username) {
        log.info("User {} yêu cầu xem Live Log SSE của Deployment ID: {}", username, deploymentId);

        // ========================================================
        // 1. XÁC THỰC & PHÂN QUYỀN (CHỐT CHẶN VÒNG NGOÀI)
        // ========================================================
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy bản ghi triển khai"));

        // Truyền projectId từ deployment vào hàm kiểm tra
        Project project = validateAccessAndGetProject(deployment.getProjectId(), username, "nghe lén log Live");

        String filePath = deployment.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new BusinessException(400, "Đường dẫn file log không hợp lệ.");
        }

        // ========================================================
        // 2. KHỞI TẠO LUỒNG SSE & CỜ HIỆU AN TOÀN
        // ========================================================
        SseEmitter emitter = new SseEmitter(0L); // Timeout 0: Không bao giờ tự ngắt

        // Biến cờ hiệu an toàn luồng (State Flag)
        AtomicBoolean isClientConnected = new AtomicBoolean(true);

        // Gắn Callback: Nếu trình duyệt tắt, ngắt mạng, hoặc timeout -> Đổi cờ thành false
        Runnable onClientDisconnect = () -> isClientConnected.set(false);
        emitter.onCompletion(onClientDisconnect);
        emitter.onTimeout(onClientDisconnect);
        emitter.onError((e) -> onClientDisconnect.run());

        // ========================================================
        // 3. THỰC THI QUÉT FILE THEO THỜI GIAN THỰC (TAIL -F)
        // ========================================================
        CompletableFuture.runAsync(() -> {
            try (RandomAccessFile reader = new RandomAccessFile(filePath, "r")) {
                long filePointer = 0;
                long lastPingTime = System.currentTimeMillis(); // THÊM GẮT: Ghi nhớ lần ping cuối

                // CHỈ TIẾP TỤC VÒNG LẶP KHI CLIENT CÒN KẾT NỐI
                while (isClientConnected.get()) {
                    long fileLength = reader.length();

                    if (fileLength > filePointer) {
                        // 3.1 CÓ DỮ LIỆU MỚI: Đọc và bơm xuống Frontend
                        reader.seek(filePointer);
                        String line;

                        while ((line = reader.readLine()) != null) {
                            String utf8Line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

                            if (utf8Line.contains("[SYSTEM_CODE:DEPLOYMENT_SUCCESS]") ||
                                    utf8Line.contains("[SYSTEM_CODE:DEPLOYMENT_FAILED]")) {
                                emitter.send(SseEmitter.event().name("EOF").data("END_OF_STREAM"));
                                emitter.complete();
                                return;
                            }

                            emitter.send(SseEmitter.event().name("log").data(utf8Line));
                            filePointer = reader.getFilePointer();
                        }
                    } else {
                        // 3.2 RẢNH RỖI: Kiểm tra xem đã qua 15 giây kể từ lần ping trước chưa
                        long now = System.currentTimeMillis();
                        if (now - lastPingTime > 15000) { // 15000ms = 15 giây
                            try {
                                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                                lastPingTime = now; // Cập nhật lại thời gian ping
                            } catch (Exception pingEx) {
                                // Client đã ngắt kết nối âm thầm -> Lật cờ và phá vòng lặp
                                isClientConnected.set(false);
                                break;
                            }
                        }
                    }

                    // Tiết kiệm CPU, vẫn ngủ nửa giây để không vắt kiệt chip
                    Thread.sleep(500);
                }

                log.info("✅ Client ngắt kết nối, đã dọn dẹp an toàn luồng đọc file log Deployment ID: {}", deploymentId);

            } catch (Exception e) {
                if (isClientConnected.get()) {
                    log.error("🔥 Lỗi chí mạng trong luồng SSE khi đọc Live Log {}: {}", filePath, e.getMessage());
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    @Override
    public PageResponse<DeploymentHistoryResponse> getProjectDeployHistories(Integer projectId, String username, int page, int size) {
        log.info("User {} yêu cầu xem lịch sử Deploy của Project ID: {} (Page: {})", username, projectId, page);

        Project project = validateAccessAndGetProject(projectId, username, "xem lịch sử");

        // 1. Tạo đối tượng Pageable (Mặc định sort đã được xử lý ở tên hàm Repository)
        Pageable pageable = PageRequest.of(page, size);

        // 2. Lấy dữ liệu phân trang từ DB
        Page<Deployment> deploymentPage = deploymentRepository.findByProjectIdOrderByIdDesc(projectId, pageable);

        // 3. Map Entity sang DTO trực tiếp trên đối tượng Page
        Page<DeploymentHistoryResponse> responsePage = deploymentPage.map(dep -> {
            String formattedMessage = dep.getCommitMessage();
            if (formattedMessage != null && !formattedMessage.trim().isEmpty()) {
                formattedMessage = formattedMessage.split("\n")[0].trim();
                if (formattedMessage.length() > 50) {
                    formattedMessage = formattedMessage.substring(0, 50) + "...";
                }
            } else {
                formattedMessage = "Không có thông báo commit";
            }

            String durationStr = "--";
            if (dep.getStartTime() != null && dep.getEndTime() != null) {
                java.time.Duration duration = java.time.Duration.between(dep.getStartTime(), dep.getEndTime());
                long minutes = duration.toMinutes();
                long seconds = duration.minusMinutes(minutes).getSeconds();

                if (minutes > 0) {
                    durationStr = minutes + "m " + seconds + "s";
                } else {
                    durationStr = seconds + "s";
                }
            } else if (dep.getStatus() == DeploymentStatus.BUILDING) {
                durationStr = "Building...";
            }

            // Xử lý Image Size (Từ Bytes -> MB)
            String formattedImageSize = "--";
            if (dep.getImageSize() != null && dep.getImageSize() > 0) {
                // Chia 1024 * 1024 để ra MB, dùng double để lấy số thập phân
                double sizeInMb = dep.getImageSize() / 1048576.0;
                formattedImageSize = String.format("%.2f MB", sizeInMb); // Làm tròn 2 chữ số thập phân
            }

            return new DeploymentHistoryResponse(
                    dep.getId(),
                    dep.getStartTime(),
                    durationStr,
                    dep.getStatus(),
                    dep.getCommitSha(),
                    formattedMessage,
                    formattedImageSize
            );
        });

        // 4. Trả về format chuẩn PageResponse của hệ thống
        return PageResponse.from(responsePage);
    }

    @Override
    public SseEmitter streamTerminalLogs(Integer projectId, String username) {
        log.info("User {} yêu cầu Terminal Logs (Runtime) cho Project ID: {}", username, projectId);

        Project project = validateAccessAndGetProject(projectId, username, "xem Terminal Log");

        // 2. Lấy ID Container của bản Deploy SUCCESS gần nhất
        Deployment latestDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElseThrow(() -> new BusinessException(400, "Dự án chưa được triển khai thành công, không có container để đọc log."));

        String containerId = latestDeploy.getContainerId();
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new BusinessException(400, "Không tìm thấy Container ID hợp lệ.");
        }

        // 3. Khởi tạo SSE (Timeout 0)
        SseEmitter emitter = new SseEmitter(0L);

        // 4. Giao việc cho DockerService
        dockerService.streamContainerLogs(
                containerId,
                200, // Cấu hình cứng lấy 200 dòng cũ theo yêu cầu
                (logLine) -> { // onNext callback
                    try {
                        emitter.send(SseEmitter.event().name("log").data(logLine));
                    } catch (Exception e) {
                        // Nếu Client đóng trình duyệt, send sẽ lỗi -> Chủ động ngắt Emitter
                        emitter.complete();
                    }
                },
                () -> { // onComplete callback
                    try {
                        emitter.send(SseEmitter.event().name("EOF").data("CONTAINER_STOPPED"));
                        emitter.complete();
                    } catch (Exception ignored) {}
                },
                (error) -> { // onError callback
                    emitter.completeWithError(error);
                }
        );

        // 5. Cấu hình giải phóng tài nguyên nếu Client chủ động ngắt
        emitter.onCompletion(() -> log.info("Đã đóng kết nối Terminal Logs an toàn."));
        emitter.onTimeout(() -> log.info("Terminal Logs kết nối bị Timeout."));

        return emitter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceStopProject(Integer projectId, String adminUsername) {
        log.info("💀 Admin {} phát lệnh FORCE STOP Project ID: {}", adminUsername, projectId);

        // 1. Kiểm tra dự án & Admin
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        var adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dữ liệu Admin"));

        Deployment latestDeploy = deploymentRepository
                .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, DeploymentStatus.SUCCESS)
                .orElseThrow(() -> new BusinessException(400, "Dự án chưa có môi trường chạy."));

        String containerId = latestDeploy.getContainerId();
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new BusinessException(400, "Không tìm thấy Container ID hợp lệ.");
        }

        // =========================================================
        // SỬA GẮT KIẾN TRÚC: LƯU DATABASE TRƯỚC, GỌI NGOẠI VI SAU CÙNG
        // =========================================================

        // 2. Cập nhật Database
        if (project.getStatus() != ProjectStatus.STOPPED) {
            project.setStatus(ProjectStatus.STOPPED);
            projectRepository.save(project);
        }

        // 3. Ghi Audit Log (Nếu 1 trong 2 bước DB lỗi, Exception sẽ ném ra ngay)
        auditLogService.logProjectAction(
                adminUser.getId(),
                ActionType.FORCE_STOP,
                projectId,
                "Admin " + adminUsername + " đã ép dừng (Force Kill) dự án " + project.getProjectName()
        );

        // ==========================================
        // CẢNH BÁO ĐỎ CHO DEVELOPER
        // ==========================================
        notificationService.sendNotification(
                project.getUser().getId(),
                project.getUser().getUsername(),
                projectId,
                "Cảnh báo: Dự án bị ép dừng",
                "Dự án của bạn vừa bị Quản trị viên (Admin) ép dừng khẩn cấp. Vui lòng kiểm tra lại cấu hình hoặc liên hệ hỗ trợ.",
                NotificationType.ERROR
        );
        // ==========================================

        // 4. Gọi Docker ở chốt chặn cuối
        // Nếu lệnh này thất bại (vd: mất kết nối Docker), Exception văng ra,
        // @Transactional sẽ lập tức ROLLBACK (hủy) toàn bộ dữ liệu ở bước 2 và 3.
        // Đảm bảo không bao giờ xảy ra lỗi State Mismatch.
        dockerService.killContainer(containerId);

        log.info("✅ Đã hoàn tất Force Stop và đồng bộ Database an toàn cho Project {}", projectId);
    }

    @Override
    public String getDeploymentLog(Integer deploymentId, String username) {
        log.info("User {} yêu cầu xem log tĩnh của Deployment ID: {}", username, deploymentId);

        // Bước 1: Xác thực tồn tại
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy bản ghi triển khai với ID: " + deploymentId));

        Project project = validateAccessAndGetProject(deployment.getProjectId(), username, "đọc log nội bộ");

        // Bước 2: Kiểm tra dữ liệu rác
        String filePath = deployment.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            return "Không có dữ liệu log cho lần triển khai này.";
        }

        // Bước 3: Kiểm tra vật lý & Đọc file
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.warn("File log vật lý không tồn tại ở đường dẫn: {}", filePath);
                return "File log vật lý đã bị xóa hoặc thất lạc khỏi hệ thống lưu trữ.";
            }

            // Đọc toàn bộ dữ liệu file thành dạng văn bản thuần túy
            return Files.readString(path);

        } catch (Exception e) {
            log.error("🔥 Lỗi I/O khi đọc file log {}: {}", filePath, e.getMessage());
            throw new BusinessException(500, "Lỗi máy chủ khi truy xuất file log.");
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

    /**
     * Hàm phụ trợ: Ghi log vật lý ra file cho tiến trình Deploy
     */
    private void writeDeployLog(String filePath, String message) {
        try {
            Path path = Paths.get(filePath);

            // 1. Đảm bảo thư mục cha (ví dụ: /var/bepaas/logs) chắc chắn phải tồn tại
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            // 2. Gắn thêm mốc thời gian cho ngầu và chuyên nghiệp
            String timePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " | ";
            String logLine = timePrefix + message + System.lineSeparator();

            // 3. Thực thi ghi file: TẠO MỚI nếu chưa có, GHI NỐI TIẾP nếu file đã tồn tại
            Files.writeString(
                    path,
                    logLine,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (Exception e) {
            log.error("❌ Hệ thống I/O lỗi, không thể ghi log ra file {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Hàm phụ trợ kiểm tra quyền truy cập dự án cho module Deployment
     * Cho phép cả Chủ sở hữu và (SYSTEM) ADMIN đi qua.
     */
    private Project validateAccessAndGetProject(Integer projectId, String username, String actionName) {
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));

        if (!isAdmin && !project.getUser().getUsername().equals(username)) {
            log.warn("🚨 Cảnh báo xâm nhập: User {} cố tình {} của Project ID {}", username, actionName, projectId);
            throw new BusinessException(403, "Bạn không có quyền " + actionName + " của dự án này");
        }

        return project;
    }

    /**
     * Hàm phụ trợ: Gửi email thông báo kết quả Deploy cho Developer
     */
    private void sendDeploymentNotification(Project project, String status, String detailMessage) {
        User owner = project.getUser();
        // Nếu User chưa cài email thì bỏ qua, không làm crash luồng
        if (owner.getEmail() == null || owner.getEmail().isBlank()) {
            return;
        }

        String developerName = owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName()
                : owner.getUsername();

        // Gắn màu sắc: Xanh cho thành công, Đỏ cho thất bại
        String statusColor = status.equals("THÀNH CÔNG") ? "#28a745" : "#dc3545";

        Map<String, Object> variables = Map.of(
                "developerName", developerName,
                "projectName", project.getProjectName(),
                "branch", project.getBranch(),
                "status", status,
                "statusColor", statusColor,
                "detailMessage", detailMessage
        );

        // Bắn mail (Luồng Async đã được cấu hình bên MailService nên rất mượt)
        mailService.sendHtmlMail(
                owner.getEmail(),
                "[Be-PaaS] Kết quả triển khai dự án: " + project.getProjectName() + " - " + status,
                "deployment-result",
                variables
        );
        log.info("📧 Đã gửi email thông báo kết quả Deploy ({}) cho User: {}", status, owner.getUsername());

        // ==========================================
        // BẮN THÔNG BÁO REAL-TIME NGAY SAU KHI GỬI MAIL
        // ==========================================
        notificationService.sendNotification(
                owner.getId(),
                owner.getUsername(),
                project.getId(),
                "Triển khai " + status,
                detailMessage, // Lấy luôn thông điệp chi tiết truyền vào (kèm URL port hoặc lỗi)
                status.equals("THÀNH CÔNG") ? NotificationType.SUCCESS : NotificationType.ERROR
        );
        // ==========================================
    }
}