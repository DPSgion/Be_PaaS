package com.be_paas.modules.project.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminMailRequest(
        @NotBlank(message = "Tiêu đề email không được để trống")
        String subject,

        @NotBlank(message = "Nội dung email không được để trống")
        String content
) {}