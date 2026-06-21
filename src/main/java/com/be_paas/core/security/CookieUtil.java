package com.be_paas.core.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${jwt.cookie.secure}")
    private boolean secure;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.cookie.same-site:}")
    private String sameSite;

    public void addTokenCookie(HttpServletResponse response, String token) {
        String cookie = buildCookie("phuong_paas", token);
        response.addHeader("Set-Cookie", cookie);
    }

    public void clearTokenCookie(HttpServletResponse response) {
        String cookie = "phuong_paas="
                + "; HttpOnly"
                + "; Path=/"
                + "; Max-Age=0";
        response.addHeader("Set-Cookie", cookie);
    }

    private String buildCookie(String name, String value) {
        String cookie = name + "=" + value
                + "; HttpOnly"
                + "; Path=/"
                + "; Max-Age=" + (expiration / 1000);

        if (sameSite != null && !sameSite.isBlank()) {
            cookie += "; SameSite=" + sameSite;
        }
        if (secure) {
            cookie += "; Secure";
        }
        return cookie;
    }
}
