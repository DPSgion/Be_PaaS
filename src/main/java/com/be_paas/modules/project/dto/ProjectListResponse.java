package com.be_paas.modules.project.dto;

import com.be_paas.modules.project.entity.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectListResponse(
        Integer id,
        String projectName,
        String domain,   // Frontend cần domain, Backend map từ cột subdomain
        String branch,
        ProjectStatus status, // BUILDING, RUNNING, STOPPED, CRASHED
        LocalDateTime createdAt
) {}