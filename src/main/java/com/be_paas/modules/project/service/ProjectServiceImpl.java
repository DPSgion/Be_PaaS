package com.be_paas.modules.project.service;

import com.be_paas.core.config.AESUtil;
import com.be_paas.core.exception.BusinessException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EnvironmentVariableRepository envVarRepository;
    private final AESUtil aesUtil;

    @Override
    @Transactional
    public Project importProject(ProjectCreateRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người dùng"));

        // Chuẩn hóa tên dự án: Chuyển thành chữ thường hoàn toàn
        String normalizedProjectName = request.projectName().toLowerCase();

        // SỬA GẮT: Kiểm tra trùng lặp trên cả 2 điều kiện (Tên dự án + Tên nhánh)
        if (projectRepository.existsByProjectNameAndBranch(normalizedProjectName, request.branch())) {
            throw new BusinessException(400, "Dự án '" + normalizedProjectName + "' với nhánh '" + request.branch() + "' đã tồn tại trong hệ thống.");
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

        return projectRepository.save(project);
    }

    @Override
    public List<ProjectListResponse> getMyProjects(String username) {
        // 1. Query danh sách dự án từ DB
        List<Project> projects = projectRepository.findByUser_UsernameAndIsDeletedFalseOrderByCreatedAtDesc(username);

        // 2. Map từ Entity sang DTO để trả cho Frontend
        return projects.stream().map(project -> {
            // Xử lý domain: Nếu chưa có tên miền (mới import), trả về null hoặc chuỗi rỗng
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

    @Override
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
        // Frontend gửi form lên, Backend không cần biết trước đó nó chứa gì,
        // chỉ cần mã hóa luôn giá trị mới mà User vừa nhập và lưu đè vào DB.
        env.setValue(aesUtil.encrypt(request.value()));

        envVarRepository.save(env);
    }

    @Transactional
    @Override
    public void deleteEnvironmentVariable(Integer projectId, Integer envId, String username) {
        Project project = getProjectIfOwnedByUser(projectId, username);

        EnvironmentVariable env = envVarRepository.findByIdAndProjectId(envId, projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy biến môi trường hợp lệ trong dự án này"));

        envVarRepository.delete(env);
    }

    @Override
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

        // Kiểm tra xem tổ hợp (Tên mới + Nhánh mới) đã bị thằng khác chiếm chưa (loại trừ chính nó)
        if (projectRepository.existsByProjectNameAndBranchAndIdNot(normalizedProjectName, request.branch(), projectId)) {
            throw new BusinessException(400, "Dự án '" + normalizedProjectName + "' với nhánh '" + request.branch() + "' đã tồn tại trong hệ thống. Vui lòng chọn tên hoặc nhánh khác.");
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


    private Project getProjectIfOwnedByUser(Integer projectId, String username) {
        // 1. Tìm dự án trong Database
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án với mã: " + projectId));

        // 2. Đối chiếu định danh người dùng
        // Lưu ý: Đảm bảo class User của bạn có field username hoặc phương thức getUsername()
        if (!project.getUser().getUsername().equals(username)) {
            log.warn("Cảnh báo bảo mật: User {} cố tình truy cập trái phép vào Project ID {}", username, projectId);
            throw new BusinessException(403, "Bạn không có quyền thao tác trên dự án này");
        }

        return project;
    }
}