package com.be_paas.modules.deployment.dto;

import com.be_paas.modules.deployment.entity.DeploymentStatus;
import java.time.LocalDateTime;

public record DeploymentHistoryResponse(
        Integer id,
        LocalDateTime startTime,
        String buildDuration,
        DeploymentStatus status,
        String commitSha,
        String commitMessage
) {}