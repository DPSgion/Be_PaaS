package com.be_paas.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnvVarRequest(
        @NotBlank(message = "Tên biến không được để trống")
        String keyName,
        @NotBlank(message = "Giá trị không được để trống")
        String value,
        @NotNull(message = "Phải xác định trạng thái bảo mật")
        Boolean isSecret
) {}