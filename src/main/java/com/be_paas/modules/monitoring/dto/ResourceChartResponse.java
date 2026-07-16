package com.be_paas.modules.monitoring.dto;

import java.util.List;

public record ResourceChartResponse(
        List<String> timestamps, // Trục X: Chứa các mốc giờ (Ví dụ: "14:05:30")
        List<Float> cpuUsages,   // Đường Line 1: % CPU
        List<Float> ramUsages    // Đường Line 2: Số MB RAM
) {}