package com.be_paas.modules.auth.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.security.CookieUtil;
import com.be_paas.core.security.JwtService;
import com.be_paas.modules.auth.dto.AuthRequest;
import com.be_paas.modules.auth.dto.AuthResponse;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CookieUtil cookieUtil; // ← thêm

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           UserRepository userRepository,
                           CookieUtil cookieUtil) { // ← thêm
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.cookieUtil = cookieUtil; // ← thêm
    }

    @Override
    public AuthResponse login(AuthRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessException(404, "User not found"));

        String token = jwtService.generateToken(authentication.getName(), user.getTokenVersion());

        cookieUtil.addTokenCookie(response, token); // ← dùng CookieUtil

        return new AuthResponse(user.getUsername(), user.getRole());
    }

    @Override
    public void logout(HttpServletResponse response) {
        cookieUtil.clearTokenCookie(response); // ← dùng CookieUtil
    }
}
