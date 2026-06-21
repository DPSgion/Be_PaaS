package com.be_paas.modules.user.dto;

import com.be_paas.modules.user.entity.Role;
import com.be_paas.modules.user.entity.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        int id,
        String email,
        String username,
        String fullname,
        String avatarUrl,
        String githubUsername,
        Role role,
        UserStatus status,
        LocalDateTime createdAt
) {
}
