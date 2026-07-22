package com.be_paas.core.security;

import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.entity.UserStatus;
import com.be_paas.modules.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // SỬA GẮT: Đổi tên hàm và sử dụng bộ quét đa luồng
        String token = extractToken(request);

        if (token != null && jwtService.isTokenValid(token)) {
            String username = jwtService.extractUsername(token);
            Integer tokenVersionFromJwt = jwtService.extractTokenVersion(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User userFromDb = userRepository.findByUsername(username).orElse(null);

                if (userFromDb != null &&
                        userFromDb.getStatus() != UserStatus.BANNED &&
                        tokenVersionFromJwt != null &&
                        tokenVersionFromJwt.equals(userFromDb.getTokenVersion())) {

                    UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                            userFromDb.getUsername(),
                            userFromDb.getPassword(),
                            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + userFromDb.getRole().name()))
                    );

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // ==========================================
    // BỘ QUÉT TOKEN THÔNG MINH
    // ==========================================
    private String extractToken(HttpServletRequest request) {
        // 1. Ưu tiên 1: Đọc từ Cookie (Luồng chuẩn của hệ thống Web)
        if (request.getCookies() != null) {
            String cookieToken = Arrays.stream(request.getCookies())
                    .filter(cookie -> "phuong_paas".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);

            if (cookieToken != null && !cookieToken.isBlank()) {
                return cookieToken;
            }
        }

        // 2. Ưu tiên 2: Đọc từ URL Parameter (Dành riêng cho EventSource / SSE của Frontend)
        String paramToken = request.getParameter("token");
        if (paramToken != null && !paramToken.isBlank()) {
            return paramToken;
        }

        return null;
    }
}