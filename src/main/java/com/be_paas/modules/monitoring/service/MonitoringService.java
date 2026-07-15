package com.be_paas.modules.monitoring.service;

import com.be_paas.modules.monitoring.dto.ProjectMetricsResponse;

public interface MonitoringService {
    /**
     * Lấy các thông số giám sát cơ bản của dự án (Container ID, Image Size)
     */
    ProjectMetricsResponse getProjectMetrics(Integer projectId, String username);
}