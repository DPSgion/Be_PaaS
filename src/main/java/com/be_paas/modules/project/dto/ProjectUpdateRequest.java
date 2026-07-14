package com.be_paas.modules.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProjectUpdateRequest(
        @NotBlank(message = "Tên dự án không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Tên dự án chỉ được chứa chữ cái không dấu, số và dấu gạch ngang")
        String projectName,

        @NotBlank(message = "Nhánh không được để trống")
        String branch,

        @NotNull(message = "Phải có port của dự án")
        @Min(value = 1, message = "Port không hợp lệ")
        Integer targetPort,

        @Pattern(regexp = "^(?!.*\\.\\.)[a-zA-Z0-9_/-]*$", message = "Đường dẫn thư mục không hợp lệ, không được chứa ký tự '..'")
        String rootDirectory
) {}