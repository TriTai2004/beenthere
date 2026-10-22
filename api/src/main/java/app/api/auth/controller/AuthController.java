package app.api.auth.controller;

import app.api.auth.dto.*;
import app.api.auth.service.AuthService;
import app.api.auth.web.AuthCookies;
import app.api.dto.response.ApiResponse;
import app.api.user.dto.UserResponse;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final AuthCookies cookies;

    public AuthController(AuthService service, AuthCookies cookies) {
        this.service = service;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", service.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var result = service.login(request);
        cookies.write(response, result);
        return ApiResponse.success("Login successful", result.user());
    }

    @PostMapping("/refresh")
    public ApiResponse<UserResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        var result = service.refresh(cookies.refreshToken(request));
        cookies.write(response, result);
        return ApiResponse.success("Session refreshed", result.user());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Xóa cookie kể cả khi database gặp lỗi; lỗi hạ tầng vẫn được handler xử lý riêng.
        try {
            service.logout(cookies.refreshToken(request));
        } finally {
            cookies.clear(response);
        }
        return ApiResponse.success("Logout successful");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(Authentication authentication) {
        return ApiResponse.success(service.me(UUID.fromString(authentication.getName())));
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(HttpServletRequest request) {
        // CSRF token không phải access/refresh token; cho frontend khác origin lấy header an toàn.
        // spa() nhận giá trị gốc qua header, không dùng token XOR trong request attribute _csrf.
        var token = ((DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName())).get();
        return ApiResponse.success(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    public record CsrfResponse(String headerName, String token) {}
}
