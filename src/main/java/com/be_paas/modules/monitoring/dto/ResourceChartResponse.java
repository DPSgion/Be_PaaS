package com.be_paas.modules.monitoring.dto;

public record ResourceChartResponse(
        String time,
        Float cpu,
        Float ram
) {}