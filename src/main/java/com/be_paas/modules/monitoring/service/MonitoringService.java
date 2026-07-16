package com.be_paas.modules.monitoring.service;

import com.be_paas.modules.monitoring.dto.ProjectMetricsResponse;

public interface MonitoringService {
    /**
     * Lấy các thông số giám sát cơ bản của dự án (Container ID, Image Size)
     */
    ProjectMetricsResponse getProjectMetrics(Integer projectId, String username);

    /**
     * Lấy dữ liệu mảng CPU/RAM theo thời gian để vẽ biểu đồ
     */
    com.be_paas.modules.monitoring.dto.ResourceChartResponse getResourceChart(Integer projectId, String username);
}