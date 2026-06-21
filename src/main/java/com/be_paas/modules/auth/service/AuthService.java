package com.be_paas.modules.auth.service;

import com.be_paas.modules.auth.dto.AuthRequest;
import com.be_paas.modules.auth.dto.AuthResponse;

public interface AuthService {
    AuthResponse  login(AuthRequest request, jakarta.servlet.http.HttpServletResponse response);
    void logout(jakarta.servlet.http.HttpServletResponse response);
}
