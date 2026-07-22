package com.be_paas.modules.project.service;

import com.be_paas.core.config.AESUtil;
import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.auditlog.entity.ActionType;
import com.be_paas.modules.auditlog.service.AuditLogService;
import com.be_paas.modules.deployment.repository.DeploymentRepository;
import com.be_paas.modules.deployment.service.DockerService;
import com.be_paas.modules.mail.service.MailService;
import com.be_paas.modules.monitoring.repository.ResourceLogRepository;
import com.be_paas.modules.notification.entity.NotificationType;
import com.be_paas.modules.notification.service.NotificationService;
import com.be_paas.modules.project.dto.*;
import com.be_paas.modules.project.entity.EnvironmentVariable;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.repository.EnvironmentVariableRepository;
import com.be_paas.modules.project.repository.ProjectRepository;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EnvironmentVariableRepository envVarRepository;
    private final AESUtil aesUtil;
    private final ResourceLogRepository resourceLogRepository;
    private final OtpService otpService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final DeploymentRepository deploymentRepository;
    private final DockerService dockerService;
    private final NotificationService notificationService;

    @Value("${app.bepaas.workspace-dir}")
    private String baseWorkspaceDir;

    @Value("${app.bepaas.docker.image-prefix}")
    private String imagePrefix;

    @Value("${app.bepaas.docker.container-prefix}")
    private String containerPrefix;

    @Override
    @Transactional
    public Project importProject(ProjectCreateRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người dùng"));

        // Chuẩn hóa tên dự án: Chuyển thành chữ thường hoàn toàn
        String normalizedProjectName = request.projectName().toLowerCase();

        // SỬA GẮT: Kiểm tra trùng lặp (User + Tên dự án + Tên nhánh)
        if (projectRepository.existsByUserAndProjectNameAndBranch(user, normalizedProjectName, request.branch())) {
            throw new BusinessException(400, "Bạn đã có dự án '" + normalizedProjectName + "' với nhánh '" + request.branch() + "' rồi. Vui lòng chọn tên hoặc nhánh khác.");
        }

        // Khởi tạo Project Entity để lưu vào DB
        Project project = new Project();
        project.setProjectName(normalizedProjectName);
        project.setBranch(request.branch());

        // Chuyển repoFullName thành URL chuẩn của GitHub và lưu thẳng vào cột duong_dan_git
        project.setGithubUrl("https://github.com/" + request.repoFullName() + ".git");

        // Gán User sở hữu
        project.setUser(user);

        // Gán targetPort
        project.setTargetPort(request.targetPort());

        // Gán đường dẫn
        String rootDir = request.rootDirectory();
        if (rootDir != null && !rootDir.isBlank()) {
            project.setRootDirectory(rootDir);
        }

        // Trạng thái ban đầu
        project.setStatus(ProjectStatus.STOPPED);

        // 1. Lưu xuống DB và hứng lại đối tượng để lấy ID
        Project savedProject = projectRepository.save(project);

        // 2. GHI AUDIT LOG
        auditLogService.logProjectAction(
                user.getId(),
                ActionType.CREATE_PROJECT,
                savedProject.getId(),
                "User " + username + " đã khởi tạo dự án: " + normalizedProjectName + " (nhánh " + request.branch() + ")"
        );

        // BẮN THÔNG BÁO REAL-TIME
        notificationService.sendNotification(
                user.getId(),
                username,
                savedProject.getId(),
                "Khởi tạo dự án thành công",
                "Dự án '" + normalizedProjectName + "' (nhánh " + request.branch() + ") đã sẵn sàng để triển khai.",
                NotificationType.SUCCESS
        );

        // 3. Trả về kết quả
        return savedProject;
    }


    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectDetail(Integer projectId, String username) {
        // Gọi hàm helper để lấy dự án và xác thực quyền sở hữu
        Project project = getProjectIfOwnedByUser(projectId, username);

        // Map từ Entity sang DTO
        return new ProjectDetailResponse(
                project.getId(),
                project.getProjectName(),
                project.getSubdomain(),
                project.getBranch(),
                project.getStatus(),
                project.getCreatedAt(),

                project.getTargetPort(),
                project.getRootDirectory(),
                project.getGithubUrl()
        );
    }

    @Transactional
    @Override
    public void addEnvironmentVariable(Integer projectId, EnvVarRequest request, String username) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        EnvironmentVariable env = new EnvironmentVariable();
        env.setProject(project);
        env.setKeyName(request.keyName());
        env.setIsSecret(request.isSecret());
        env.setValue(aesUtil.encrypt(request.value())); // Mã hóa giá trị mới

        envVarRepository.save(env);

        // GHI AUDIT LOG: LƯU Ý CHỈ GHI TÊN BIẾN (KEY)
        auditLogService.logProjectAction(
                project.getUser().getId(),
                ActionType.CREATE_ENV,
                projectId,
                "User " + username + " đã THÊM biến môi trường: " + request.keyName()
        );
    }

    @Transactional
    @Override
    public void updateEnvironmentVariable(Integer projectId, Integer envId, EnvVarRequest request, String username) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        EnvironmentVariable env = envVarRepository.findByIdAndProjectId(envId, projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy biến môi trường hợp lệ trong dự án này"));

        env.setKeyName(request.keyName());
        env.setIsSecret(request.isSecret());

        // LOGIC CHUẨN GITHUB ACTIONS:
        env.setValue(aesUtil.encrypt(request.value()));

        envVarRepository.save(env);

        // GHI AUDIT LOG: LƯU Ý CHỈ GHI TÊN BIẾN (KEY)
        auditLogService.logProjectAction(
                project.getUser().getId(),
                ActionType.UPDATE_ENV,
                projectId,
                "User " + username + " đã CẬP NHẬT biến môi trường: " + request.keyName()
        );
    }

    @Transactional
    @Override
    public void deleteEnvironmentVariable(Integer projectId, Integer envId, String username) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        EnvironmentVariable env = envVarRepository.findByIdAndProjectId(envId, projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy biến môi trường hợp lệ trong dự án này"));

        // Lấy tên biến ra trước khi xóa để ghi log
        String deletedKey = env.getKeyName();

        envVarRepository.delete(env);

        // GHI AUDIT LOG
        auditLogService.logProjectAction(
                project.getUser().getId(),
                ActionType.DELETE_ENV,
                projectId,
                "User " + username + " đã XÓA biến môi trường: " + deletedKey
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvVarResponse> getEnvironmentVariables(Integer projectId, String username) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        return envVarRepository.findByProjectId(projectId).stream().map(env -> {
            String displayValue;
            if (env.getIsSecret()) {
                displayValue = "********"; // Che giá trị lại nếu la_bi_mat = true
            } else {
                displayValue = aesUtil.decrypt(env.getValue()); // Giải mã để hiển thị
            }
            return new EnvVarResponse(env.getId(), env.getKeyName(), displayValue, env.getIsSecret());
        }).toList();
    }

    @Override
    @Transactional
    public void updateProjectSettings(Integer projectId, ProjectUpdateRequest request, String username) {
        log.info("User {} yêu cầu cập nhật cấu hình Project ID: {}", username, projectId);

        // 1. Tìm dự án
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));

        // 2. Bảo mật: Xác thực quyền sở hữu
        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo xâm nhập: User {} cố gắng sửa Project {} của User {}",
                    username, projectId, project.getUser().getUsername());
            throw new BusinessException(403, "Bạn không có quyền chỉnh sửa dự án này");
        }

        // ==========================================
        // SỬA GẮT: KIỂM TRA LOGIC TRÙNG LẶP & CHUẨN HÓA DỮ LIỆU
        // ==========================================
        String normalizedProjectName = request.projectName().toLowerCase();

        // Chỉ kiểm tra trùng lặp trong phạm vi các dự án CỦA CHÍNH USER NÀY
        if (projectRepository.existsByUserAndProjectNameAndBranchAndIdNot(project.getUser(), normalizedProjectName, request.branch(), projectId)) {
            throw new BusinessException(400, "Bạn đã có dự án '" + normalizedProjectName + "' với nhánh '" + request.branch() + "'. Vui lòng chọn tên hoặc nhánh khác.");
        }

        // 3. Cập nhật các trường thông tin
        project.setProjectName(normalizedProjectName); // Đã chuẩn hóa chữ thường
        project.setBranch(request.branch());
        project.setTargetPort(request.targetPort());

        // Xử lý chuỗi rỗng thành null để DB gọn gàng (nếu cần)
        String rootDir = request.rootDirectory();
        if (rootDir != null && rootDir.trim().isEmpty()) {
            rootDir = null;
        }
        project.setRootDirectory(rootDir);

        // 4. Lưu xuống Database
        projectRepository.save(project);
        log.info("Cập nhật thành công cấu hình cho Project ID: {}", projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProjectListResponse> getAdminProjects(String projectName, String developer, ProjectStatus status, Pageable pageable) {

        // 1. Quét danh sách dự án kèm phân trang và bộ lọc
        Page<Project> projects = projectRepository.findProjectsForAdmin(projectName, status, developer, pageable);

        // 2. Chuyển đổi Entity sang DTO và đắp số liệu CPU/RAM
        Page<AdminProjectListResponse> dtoPage = projects.map(project -> {
            Float cpu = null;
            Float ram = null;

            // Chặn N+1 Query: Chỉ lấy số liệu nếu dự án đang RUNNING
            if (ProjectStatus.RUNNING.equals(project.getStatus())) {
                var latestLog = resourceLogRepository.findFirstByProjectIdOrderByCreatedAtDesc(project.getId());
                if (latestLog.isPresent()) {
                    cpu = latestLog.get().getCpuUsage();
                    ram = latestLog.get().getRamUsage();
                }
            }

            return new AdminProjectListResponse(
                    project.getId(),
                    project.getProjectName(),
                    project.getUser().getUsername(),
                    project.getBranch(),
                    project.getSubdomain(),
                    project.getStatus(),
                    cpu,
                    ram
            );
        });

        return PageResponse.from(dtoPage);
    }

    @Override
    public List<ProjectListResponse> getMyProjects(String username, String projectName, ProjectStatus status) {
        // 1. Query danh sách dự án từ DB có áp dụng bộ lọc (SỬA GẮT)
        List<Project> projects = projectRepository.findMyProjectsWithFilters(username, projectName, status);

        // 2. Map từ Entity sang DTO để trả cho Frontend (Giữ nguyên)
        return projects.stream().map(project -> {
            String displayDomain = project.getSubdomain();

            return new ProjectListResponse(
                    project.getId(),
                    project.getProjectName(),
                    displayDomain,
                    project.getBranch(),
                    project.getStatus(),
                    project.getCreatedAt()
            );
        }).toList();
    }

    // =========================================
    // HÀM 1: YÊU CẦU XÓA (GỬI MAIL)
    // =========================================
    @Override
    @Transactional(readOnly = true)
    public void requestDeleteProject(Integer projectId, String username) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        // 1. Tạo Key định danh duy nhất cho phiên xóa này
        String otpKey = "DELETE_PROJECT_" + projectId + "_" + username;

        // 2. Sinh mã lưu vào RAM
        String otpCode = otpService.generateAndSaveOtp(otpKey);

        // 3. Chuẩn bị dữ liệu gửi Mail theo chuẩn của bạn
        Map<String, Object> variables = Map.of(
                "projectName", project.getProjectName(),
                "otpCode", otpCode,
                "developerName", project.getUser().getFullName() != null ? project.getUser().getFullName() : username
        );

        // 4. Bắn Mail (Luồng Async không làm nghẽn API)
        mailService.sendHtmlMail(
                project.getUser().getEmail(),
                "[Be-PaaS] Mã xác nhận xóa dự án " + project.getProjectName(),
                "otp-delete-project", // Bạn cần tạo file otp-delete-project.html trong thư mục templates
                variables
        );

        log.info("Đã gửi OTP xóa Project {} tới email của User {}", projectId, username);
    }

    // =========================================
    // HÀM 2: XÁC NHẬN MÃ VÀ THỰC THI XÓA
    // =========================================
    @Override
    @Transactional
    public void confirmDeleteProject(Integer projectId, String username, String otpCode) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        String otpKey = "DELETE_PROJECT_" + projectId + "_" + username;

        // 1. Kiểm chứng mã OTP
        if (!otpService.validateOtp(otpKey, otpCode)) {
            throw new BusinessException(400, "Mã xác nhận không chính xác hoặc đã hết hạn.");
        }

        // 2. MÃ HỢP LỆ -> TIẾN HÀNH "KHAI TỬ" HẠ TẦNG DOCKER
        log.info("Mã OTP hợp lệ. Đang tiến hành xóa toàn bộ dữ liệu Project ID: {}", projectId);
        String containerName = containerPrefix + projectId;
        String imageName = imagePrefix + projectId;
        dockerService.cleanupContainerAndImage(containerName, imageName);

        // ========================================================
        // 3. GỌI "MÁY HÚT BỤI" DỌN RÁC VẬT LÝ (Chạy ngầm Async)
        // ========================================================
        cleanupPhysicalFiles(projectId);

        // ========================================================
        // 4. ĐỔI TÊN VÀ XÓA MỀM (Giải phóng Namespace, Port, Domain)
        // ========================================================
        String oldProjectName = project.getProjectName();
        String shortHash = Long.toString(System.currentTimeMillis(), 36);
        String deletedName = String.format("%s-%s-deleted-%s-%s",
                project.getProjectName(),
                project.getBranch(),
                username,
                shortHash);

        project.setProjectName(deletedName);
        project.setIsDeleted(true);

        // Giải phóng Port và Subdomain về null để nhả tài nguyên cho người khác dùng
        project.setInternalPort(null);
        project.setSubdomain(null);

        projectRepository.save(project);

        // BẮN THÔNG BÁO REAL-TIME
        notificationService.sendNotification(
                project.getUser().getId(),
                username,
                projectId,
                "Dự án đã bị xóa",
                "Dự án '" + oldProjectName + "' đã được xóa và thu hồi toàn bộ tài nguyên hạ tầng.",
                NotificationType.WARNING
        );

        // 5. GHI AUDIT LOG
        auditLogService.logProjectAction(
                project.getUser().getId(),
                ActionType.DELETE_PROJECT,
                projectId,
                "User " + username + " đã xóa dự án, thu hồi tài nguyên Port/Domain: " + oldProjectName + " (đổi thành: " + deletedName + ")"
        );

        log.info("Đã xóa hoàn tất. Project ID {} được đổi tên thành: {}", projectId, deletedName);
    }

    @Override
    @Transactional
    public void sendAdminNoticeMail(Integer projectId, AdminMailRequest request, String adminUsername) {
        // 1. Tìm dự án và xác định chủ sở hữu
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án với ID: " + projectId));

        User owner = project.getUser();
        if (owner.getEmail() == null || owner.getEmail().isBlank()) {
            throw new BusinessException(400, "Không thể gửi thư: Developer này chưa cấu hình địa chỉ Email.");
        }

        // 2. Chuẩn bị dữ liệu cho Thymeleaf
        String developerName = owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName()
                : owner.getUsername();

        // Lấy username
        String developerUsername = owner.getUsername();

        Map<String, Object> variables = Map.of(
                "developerName", developerName,
                "projectName", project.getProjectName(),
                "adminMessage", request.content()
        );

        // 3. Thực thi gửi mail thông qua MailService đã có
        mailService.sendHtmlMail(
                owner.getEmail(),
                "[Be-PaaS Admin] " + request.subject(),
                "admin-notice", // Tên file template giao diện (tạo ở Bước 3)
                variables
        );

        // BẮN THÔNG BÁO REAL-TIME CHO DEVELOPER TRÊN WEB
        notificationService.sendNotification(
                owner.getId(),
                owner.getUsername(),
                projectId,
                "Thông báo từ Admin: " + request.subject(),
                request.content(),
                NotificationType.INFO
        );

        log.info("Admin [{}] đã gửi mail thông báo cho User [{}] về Project [{}]", adminUsername, owner.getUsername(), project.getProjectName());

        // 4. Ghi Audit Log
        auditLogService.logProjectAction(
                userRepository.findByUsername(adminUsername).get().getId(), // Lấy ID admin
                ActionType.SEND_MAIL,
                projectId,
                "Admin " + adminUsername + " đã gửi email đến " + developerUsername + " để thông báo với tiêu đề: " + request.subject()
        );
    }

    private Project getProjectIfOwnedByUser(Integer projectId, String username) {
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án với mã: " + projectId));

        // 2. Lấy danh sách quyền hạn của người đang gọi API từ Spring Security Context
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));

        // Nếu là Admin hoặc System Admin thì cho phép đi qua ngay lập tức
        if (isAdmin) {
            return project;
        }

        // 3. Nếu chỉ là Developer bình thường, bắt buộc phải đúng tên chủ sở hữu
        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo bảo mật: User {} cố tình truy cập trái phép vào Project ID {}", username, projectId);
            throw new BusinessException(403, "Bạn không có quyền thao tác trên dự án này");
        }

        return project;
    }

    /**
     * Hàm dọn dẹp các tệp tin vật lý (Workspace & File log) chạy ngầm
     */
    private void cleanupPhysicalFiles(Integer projectId) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Dọn dẹp thư mục Workspace (Clone code)
                if (baseWorkspaceDir != null) {
                    Path workspacePath = Paths.get(baseWorkspaceDir, "project_" + projectId);
                    if (Files.exists(workspacePath)) {
                        forceDeleteDirectory(workspacePath);
                        log.info("🧹 Đã dọn sạch thư mục mã nguồn vật lý: {}", workspacePath);
                    }
                }

                // 2. Dọn dẹp File Log Deployment
                // Lấy tối đa 1000 bản ghi lịch sử gần nhất để dò tìm file log[cite: 14]
                var deployments = deploymentRepository.findByProjectIdOrderByIdDesc(projectId, PageRequest.of(0, 1000));
                for (var deploy : deployments) {
                    if (deploy.getFilePath() != null) {
                        Files.deleteIfExists(Paths.get(deploy.getFilePath()));
                    }
                }
                log.info("🧹 Đã dọn dẹp toàn bộ file log Deployment của Project ID: {}", projectId);

            } catch (Exception e) {
                log.error("🔥 Lỗi dọn dẹp file vật lý cho Project ID {}: {}", projectId, e.getMessage());
            }
        });
    }

    /**
     * Hàm đệ quy xóa thư mục ép buộc (Bypass lỗi AccessDenied của file Git)
     */
    private void forceDeleteDirectory(Path path) throws java.io.IOException {
        Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                file.toFile().setWritable(true); // Mở khóa Read-Only[cite: 17]
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc) throws java.io.IOException {
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}