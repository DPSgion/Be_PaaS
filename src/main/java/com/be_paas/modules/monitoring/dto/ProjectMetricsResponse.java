package com.be_paas.modules.monitoring.dto;

public record ProjectMetricsResponse(
        String containerId,
        Double imageSize
) {}