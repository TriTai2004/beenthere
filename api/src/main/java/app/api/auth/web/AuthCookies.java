package app.api.auth.web;

import app.api.auth.config.AuthCookieProperties;
import app.api.auth.service.AuthTokens;
import jakarta.servlet.http.*;
import java.time.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    private final AuthCookieProperties properties;

    public AuthCookies(AuthCookieProperties properties) { this.properties = properties; }

    public void write(HttpServletResponse response, AuthTokens tokens) {
        add(response, "access_token", tokens.accessToken(), "/", remaining(tokens.accessExpiresAt()));
        add(response, "refresh_token", tokens.refreshToken(), "/api/auth", remaining(tokens.refreshExpiresAt()));
    }

    public void clear(HttpServletResponse response) {
        add(response, "access_token", "", "/", Duration.ZERO);
        add(response, "refresh_token", "", "/api/auth", Duration.ZERO);
    }

    public String refreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        String value = null;
        boolean found = false;
        for (Cookie cookie : request.getCookies()) {
            if ("refresh_token".equals(cookie.getName())) {
                if (found) return null;
                found = true;
                value = cookie.getValue();
            }
        }
        return value;
    }

    private Duration remaining(Instant expiresAt) {
        return Duration.ofSeconds(Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds()));
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        var cookie = ResponseCookie.from(name, value).httpOnly(true).secure(properties.secure())
                .sameSite(properties.sameSite()).path(path).maxAge(maxAge);
        if (properties.domain() != null) cookie.domain(properties.domain());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
