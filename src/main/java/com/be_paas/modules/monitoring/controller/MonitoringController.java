package com.be_paas.modules.monitoring.controller;

import com.be_paas.modules.monitoring.dto.ProjectMetricsResponse;
import com.be_paas.modules.monitoring.dto.ResourceChartResponse;
import com.be_paas.modules.monitoring.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/projects/{projectId}/metrics")
    public ResponseEntity<ProjectMetricsResponse> getProjectMetrics(
            @PathVariable Integer projectId,
            Authentication authentication) {

        String username = authentication.getName();
        ProjectMetricsResponse response = monitoringService.getProjectMetrics(projectId, username);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/projects/{projectId}/metrics/chart")
    public ResponseEntity<ResourceChartResponse> getResourceChart(
            @PathVariable Integer projectId,
            Authentication authentication) {

        String username = authentication.getName();
        ResourceChartResponse response = monitoringService.getResourceChart(projectId, username);

        return ResponseEntity.ok(response);
    }
}