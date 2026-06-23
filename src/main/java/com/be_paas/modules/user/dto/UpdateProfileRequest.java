package com.be_paas.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        String fullName
) {
}
