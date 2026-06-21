package com.be_paas.modules.user.dto;

import com.be_paas.modules.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddNewUser(
        @NotBlank(message = "Name is required and must not be blank")
        String email,

        @NotBlank(message = "Username is required and must not be blank")
        String username,

        @NotBlank(message = "Password is required and must not be blank")
        String password,

        @NotBlank(message = "fullName is required and must not be blank")
        String fullName,

        @NotNull(message = "Role is required")
        Role role,

        String avatarUrl
) {
}
