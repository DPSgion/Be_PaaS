package com.be_paas.modules.project.dto;

import com.be_paas.modules.project.entity.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectDetailResponse(
        Integer id,
        String projectName,
        String domain,
        String branch,
        ProjectStatus status,
        LocalDateTime createdAt,

        Integer targetPort,
        String rootDirectory,
        String githubUrl
) {}