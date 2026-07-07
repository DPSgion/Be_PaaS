package com.be_paas.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProjectCreateRequest(
        @NotBlank(message = "Tên dự án không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Tên dự án chỉ được chứa chữ cái không dấu, số và dấu gạch ngang")
        String projectName,

        @NotBlank(message = "Repository không được để trống")
        String repoFullName,

        @NotBlank(message = "Nhánh không được để trống")
        String branch
) {
}
