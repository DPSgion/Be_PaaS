package com.be_paas.modules.monitoring.service;

import com.be_paas.modules.monitoring.dto.ProjectMetricsResponse;
import com.be_paas.modules.monitoring.dto.ResourceChartResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MonitoringService {
    /**
     * Lấy các thông số giám sát cơ bản của dự án (Container ID, Image Size, Domain)
     */
    ProjectMetricsResponse getProjectMetrics(Integer projectId, String username);

    /**
     * Lấy dữ liệu mảng CPU/RAM theo thời gian để vẽ biểu đồ
     */
    List<ResourceChartResponse> getResourceChart(Integer projectId, String username);

    SseEmitter subscribe(Integer projectId, String username);
    void sendChartData(Integer projectId, ResourceChartResponse data);
}