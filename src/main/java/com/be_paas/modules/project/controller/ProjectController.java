package com.be_paas.modules.project.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.project.dto.*;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectListResponse>> getMyProjects(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) ProjectStatus status
    ) {
        // Lấy username của người dùng đang đăng nhập từ Security Context
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Gọi Service truyền thêm biến lọc và trả về HTTP 200 OK
        List<ProjectListResponse> projects = projectService.getMyProjects(currentUsername, projectName, status);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getProjectDetail(@PathVariable Integer projectId) {
        // Lấy định danh user từ token hiện tại
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Gọi service xử lý và trả dữ liệu
        ProjectDetailResponse response = projectService.getProjectDetail(projectId, currentUsername);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/import")
    public ResponseEntity<Project> importProject(@Valid @RequestBody ProjectCreateRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        Project savedProject = projectService.importProject(request, currentUsername);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedProject);
    }


    @PostMapping("/{projectId}/envs")
    public ResponseEntity<Void> addEnvVar(@PathVariable Integer projectId, @Valid @RequestBody EnvVarRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        projectService.addEnvironmentVariable(projectId, request, currentUsername);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{projectId}/envs/{envId}")
    public ResponseEntity<Void> updateEnvVar(
            @PathVariable Integer projectId,
            @PathVariable Integer envId,
            @Valid @RequestBody EnvVarRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        projectService.updateEnvironmentVariable(projectId, envId, request, currentUsername);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{projectId}/envs/{envId}")
    public ResponseEntity<Void> deleteEnvVar(@PathVariable Integer projectId, @PathVariable Integer envId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        projectService.deleteEnvironmentVariable(projectId, envId, currentUsername);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/envs")
    public ResponseEntity<List<EnvVarResponse>> getEnvs(@PathVariable Integer projectId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(projectService.getEnvironmentVariables(projectId, currentUsername));
    }

    @PutMapping("/{projectId}/settings")
    public ResponseEntity<String> updateProjectSettings(
            @PathVariable Integer projectId,
            @Valid @RequestBody ProjectUpdateRequest request
    ) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        projectService.updateProjectSettings(projectId, request, currentUsername);

        return ResponseEntity.ok("Cập nhật cấu hình dự án thành công. Bạn cần Deploy lại để áp dụng thay đổi.");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<PageResponse<AdminProjectListResponse>> getAllProjectsForAdmin(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String developer,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        PageResponse<AdminProjectListResponse> result = projectService.getAdminProjects(projectName, developer, status, pageable);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{projectId}/delete/request")
    public ResponseEntity<String> requestDeleteProject(@PathVariable Integer projectId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        projectService.requestDeleteProject(projectId, currentUsername);

        return ResponseEntity.ok("Mã xác nhận đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư.");
    }

    @DeleteMapping("/{projectId}/delete/confirm")
    public ResponseEntity<String> confirmDeleteProject(
            @PathVariable Integer projectId,
            @RequestBody OtpConfirmRequest request) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        projectService.confirmDeleteProject(projectId, currentUsername, request.otpCode());

        return ResponseEntity.ok("Dự án đã được xóa thành công khỏi hệ thống.");
    }

    @PostMapping("/admin/{projectId}/send-mail")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<String> sendNoticeMailToDeveloper(
            @PathVariable Integer projectId,
            @Valid @RequestBody AdminMailRequest request) {

        // Lấy username của Admin đang đăng nhập
        String adminUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        projectService.sendAdminNoticeMail(projectId, request, adminUsername);

        return ResponseEntity.ok("Đã gửi email thông báo thành công tới chủ sở hữu dự án.");
    }
}