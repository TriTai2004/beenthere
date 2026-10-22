package app.api.security.jwt;

import app.api.config.SecurityConfig;
import app.api.user.entity.User;
import app.api.user.repository.UserRepository;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.mockito.Mockito.*;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = JwtSecurityIntegrationTest.TestEndpoints.class)
@Import({SecurityConfig.class, JwtSecurityIntegrationTest.JwtTestConfiguration.class,
        JwtSecurityIntegrationTest.TestEndpoints.class})
class JwtSecurityIntegrationTest {
    private static final UUID USER_ID = UUID.randomUUID();
    @MockitoBean private UserRepository users;
    @MockitoBean private UserDetailsService userDetailsService;
    @BeforeEach
    void userExists() {
        var user = new User();
        user.setId(USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }
    private static final byte[] KEY = new SecureRandom().generateSeed(32);
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private SecurityFilterChain securityFilterChain;

    @Test
    void protectedEndpointRequiresCookieAndIgnoresBearer() throws Exception {
        mvc.perform(get("/protected")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/protected").header("Authorization", "Bearer " + jwtUtil.generateToken("user")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCookieAuthenticatesOnlyCurrentRequest() throws Exception {
        var result = mvc.perform(get("/protected").cookie(accessCookie()))
                .andExpect(status().isOk()).andExpect(content().string(USER_ID.toString())).andReturn();
        assertNull(result.getRequest().getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        mvc.perform(get("/protected")).andExpect(status().isUnauthorized());
        assertEquals(1, securityFilterChain.getFilters().stream()
                .filter(JwtAuthenticationFilter.class::isInstance).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "signature", "issuer", "malformed", "blank"})
    void invalidTokensNeverAuthenticateOrExposeDetails(String kind) throws Exception {
        String token = switch (kind) {
            case "expired" -> JWT.create().withIssuer("beenthere").withSubject("user")
                    .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(KEY));
            case "signature" -> new JwtUtil(Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)),
                    "beenthere", Duration.ofMinutes(15)).generateToken("user");
            case "issuer" -> new JwtUtil(Base64.getEncoder().encodeToString(KEY),
                    "other-issuer", Duration.ofMinutes(15)).generateToken("user");
            case "blank" -> "";
            default -> "invalid.token";
        };
        mvc.perform(get("/protected").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
        mvc.perform(get("/api/auth/csrf").cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/test-public", "/api/auth/me", "/swagger-ui.html", "/v3/api-docs/test-public"})
    void nonPublicPathsRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfCookieAndMatchingHeaderAreRequiredForWrites() throws Exception {
        mvc.perform(post("/protected").cookie(accessCookie())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        // Dùng cookie/header thật từ security chain, không dùng helper bỏ qua repository CSRF.
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie csrf = response.getCookie("XSRF-TOKEN");
        assertNotNull(csrf);
        assertFalse(csrf.isHttpOnly());
        mvc.perform(post("/protected").cookie(accessCookie(), csrf))
                .andExpect(status().isForbidden());
        mvc.perform(post("/protected").cookie(accessCookie(), csrf).header("X-XSRF-TOKEN", "incorrect"))
                .andExpect(status().isForbidden());
        var result = mvc.perform(post("/protected").cookie(accessCookie(), csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk()).andExpect(content().string(USER_ID.toString())).andReturn();
        assertNull(result.getRequest().getSession(false));
        mvc.perform(post("/protected").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/test-public")).andExpect(status().isForbidden());
    }

    @Test
    void trustedCorsPreflightIsAllowed() throws Exception {
        mvc.perform(options("/protected").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private Cookie accessCookie() {
        var cookie = new Cookie("access_token", jwtUtil.generateToken(USER_ID.toString()));
        cookie.setHttpOnly(true);
        return cookie;
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil(Base64.getEncoder().encodeToString(KEY), "beenthere", Duration.ofMinutes(15));
        }
    }

    // Chỉ tồn tại trong test, không thêm endpoint authentication vào ứng dụng.
    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/auth/csrf", "/api/auth/test-public", "/swagger-ui/test-public", "/swagger-ui.html", "/v3/api-docs/test-public"})
        String publicEndpoint() { return "public"; }

        @GetMapping("/protected")
        String protectedEndpoint(Authentication authentication) { return authentication.getName(); }

        @PostMapping("/protected")
        String protectedWrite(Authentication authentication) { return authentication.getName(); }

        @PostMapping("/api/auth/test-public")
        String publicWrite() { return "public"; }
    }
}
