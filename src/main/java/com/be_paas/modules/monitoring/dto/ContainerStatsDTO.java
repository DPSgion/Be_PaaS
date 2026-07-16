package com.be_paas.modules.monitoring.dto;

public record ContainerStatsDTO(
        Float cpuUsage, // Trả về phần trăm (Ví dụ: 1.5%)
        Float ramUsage  // Trả về số MB (Ví dụ: 120.5 MB)
) {}