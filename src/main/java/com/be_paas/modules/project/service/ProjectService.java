package com.be_paas.modules.project.service;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.project.dto.*;
import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProjectService {
    Project importProject(ProjectCreateRequest request, String username);
    ProjectDetailResponse getProjectDetail(Integer projectId, String username);

    void addEnvironmentVariable(Integer projectId, EnvVarRequest request, String username);
    void updateEnvironmentVariable(Integer projectId, Integer envId, EnvVarRequest request, String username);
    void deleteEnvironmentVariable(Integer projectId, Integer envId, String username);

    List<EnvVarResponse> getEnvironmentVariables(Integer projectId, String username);

    void updateProjectSettings(Integer projectId, ProjectUpdateRequest request, String username);

    PageResponse<AdminProjectListResponse> getAdminProjects(String projectName, String developer, ProjectStatus status, Pageable pageable);

    List<ProjectListResponse> getMyProjects(String username, String projectName, ProjectStatus status);

    void requestDeleteProject(Integer projectId, String username);
    void confirmDeleteProject(Integer projectId, String username, String otpCode);
}
