package com.be_paas.modules.user.dto;

import com.be_paas.modules.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull(message = "Role là bắt buộc")
        Role role
) {
}
