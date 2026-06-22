package com.be_paas.modules.user.dto;

import com.be_paas.modules.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull(message = "Status is required")
        UserStatus status,

        String reason
) {
}
