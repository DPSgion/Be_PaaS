package com.be_paas.modules.user.dto;

public record ResetPasswordResponse(
        String username,
        String newPassword
) {
}
