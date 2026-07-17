package com.be_paas.modules.project.dto;

import com.be_paas.modules.project.entity.ProjectStatus;

public record AdminProjectListResponse(
        Integer projectId,
        String projectName,
        String ownerUsername,
        String branch,
        String subdomain,
        ProjectStatus status,
        Float cpuUsage,
        Float ramUsage
) {}