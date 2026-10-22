package app.api.security.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import app.api.user.enums.UserStatus;
import app.api.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository users) {
        this.jwtUtil = jwtUtil;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // Không ghi đè xác thực do cơ chế khác thiết lập trước đó.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = accessToken(request);
            if (token != null) {
                UUID userId = verifiedUserId(token);
                if (userId != null) {
                    // Quyền/trạng thái lấy từ database để khóa hoặc xóa user có hiệu lực ngay.
                    users.findById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                            .ifPresent(user -> {
                                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                                        user.getId().toString(), null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                                var context = SecurityContextHolder.createEmptyContext();
                                context.setAuthentication(authentication);
                                SecurityContextHolder.setContext(context);
                            });
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private UUID verifiedUserId(String token) {
        try {
            // Xác minh trước khi dùng subject; không bắt lỗi database hoặc lỗi từ filter phía sau.
            return UUID.fromString(jwtUtil.extractUserId(token));
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String accessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String token = null;
        boolean found = false;
        for (Cookie cookie : cookies) {
            if ("access_token".equals(cookie.getName())) {
                // Từ chối cookie trùng tên để tránh chọn nhầm token theo thứ tự gửi lên.
                if (found) {
                    return null;
                }
                found = true;
                token = cookie.getValue();
            }
        }
        return token;
    }
}
