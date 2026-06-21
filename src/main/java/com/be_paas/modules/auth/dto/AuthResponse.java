package com.be_paas.modules.auth.dto;

import com.be_paas.modules.user.entity.Role;

public record AuthResponse(
        String username,
        Role role
) {}