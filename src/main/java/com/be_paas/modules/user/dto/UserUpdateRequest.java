package com.be_paas.modules.user.dto;

public record UserUpdateRequest(
        String password,
        String fullName,
        String avatarUrl
) {
}