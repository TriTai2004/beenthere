package app.api.auth;

import app.api.auth.dto.*;
import app.api.auth.exception.InvalidRefreshTokenException;
import app.api.auth.service.*;
import app.api.exception.BusinessException;
import app.api.security.jwt.JwtUtil;
import app.api.support.PostgresIntegrationTest;
import app.api.user.entity.User;
import app.api.user.enums.*;
import app.api.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends PostgresIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final List<UUID> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanOwnData() {
        // Chỉ dọn user do test này tạo; FK cascade dọn phiên/token của user đó.
        createdUsers.forEach(id -> jdbc.update("delete from users where id = ?", id));
    }

    @Test
    void registerNormalizesHashesAndDoesNotLogin() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var response = mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", " " + email.toUpperCase(Locale.ROOT) + " ",
                                "password", PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist()).andReturn().getResponse();
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        createdUsers.add(user.getId());
        assertEquals(email, user.getEmail());
        assertEquals(UserRole.USER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(encoder.matches(PASSWORD, user.getPasswordHash()));
        assertNull(response.getCookie("access_token"));
        assertNull(response.getCookie("refresh_token"));
        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(Locale.ROOT), PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void loginCookiesMeRotationReplayAndLogout() throws Exception {
        User user = register();
        var login = mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse();
        Cookie access = login.getCookie("access_token");
        Cookie refresh = login.getCookie("refresh_token");
        assertNotNull(access);
        assertNotNull(refresh);
        assertTrue(access.isHttpOnly());
        assertTrue(refresh.isHttpOnly());
        assertEquals("/", access.getPath());
        assertEquals("/api/auth", refresh.getPath());
        assertEquals("Lax", access.getAttribute("SameSite"));
        assertTrue(access.getMaxAge() > 0 && access.getMaxAge() <= 900);
        assertTrue(refresh.getMaxAge() > 0 && refresh.getMaxAge() <= 30 * 86400);
        assertEquals(user.getId().toString(), jwt.extractUserId(access.getValue()));
        assertEquals(43, refresh.getValue().length());
        String storedHash = jdbc.queryForObject("select t.token_hash from auth_refresh_tokens t join "
                + "auth_refresh_sessions s on s.id=t.session_id where s.user_id=?", String.class, user.getId());
        assertEquals(64, storedHash.length());
        assertNotEquals(refresh.getValue(), storedHash);
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        // Refresh vẫn thành công khi access cookie hỏng/hết hạn.
        var rotated = mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(refresh, new Cookie("access_token", "expired")))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
        assertNotNull(rotated);
        assertNotEquals(refresh.getValue(), rotated.getValue());
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(refresh)).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(rotated)).andExpect(status().isUnauthorized());

        var newLogin = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        var logout = mvc.perform(withCsrf(post("/api/auth/logout"))
                        .cookie(new Cookie("refresh_token", newLogin.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(0, logout.getCookie("access_token").getMaxAge());
        assertEquals(0, logout.getCookie("refresh_token").getMaxAge());
        assertEquals("/api/auth", logout.getCookie("refresh_token").getPath());
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(newLogin.refreshToken()));
        mvc.perform(withCsrf(post("/api/auth/logout"))).andExpect(status().isOk());
    }

    @Test
    void missingWrongPasswordDisabledAndPasswordlessHaveSameLoginError() throws Exception {
        User user = register();
        assertLoginDenied(user.getEmail(), "wrong-password");
        assertLoginDenied(UUID.randomUUID() + "@example.com", PASSWORD);
        for (UserStatus state : List.of(UserStatus.SUSPENDED, UserStatus.BANNED)) {
            user.setStatus(state);
            users.saveAndFlush(user);
            assertLoginDenied(user.getEmail(), PASSWORD);
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(null);
        users.saveAndFlush(user);
        assertLoginDenied(user.getEmail(), PASSWORD);
    }

    @Test
    void accessAndRefreshRejectDisabledOrDeletedUser() throws Exception {
        User user = register();
        var login = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        user.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(user);
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", login.accessToken())))
                .andExpect(status().isUnauthorized());
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(login.refreshToken()));
        jdbc.update("delete from users where id=?", user.getId());
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", login.accessToken())))
                .andExpect(status().isUnauthorized());
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(login.refreshToken()));
    }

    @Test
    void missingDuplicateUnknownAndExpiredRefreshCookiesAreRejected() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/refresh"))).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("refresh_token", "a".repeat(43))))
                .andExpect(status().isUnauthorized());
        User user = register();
        var login = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        Cookie cookie = new Cookie("refresh_token", login.refreshToken());
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(cookie, cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", login.accessToken()),
                        new Cookie("access_token", login.accessToken())))
                .andExpect(status().isUnauthorized());
        jdbc.update("update auth_refresh_tokens set expires_at=? where session_id in "
                + "(select id from auth_refresh_sessions where user_id=?)", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.getId());
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post("/api/auth/logout")).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/auth/logout")).cookie(cookie, cookie)).andExpect(status().isOk());
    }

    @Test
    void simultaneousRefreshHasOneWinnerAndRevokesTheFamily() throws Exception {
        User user = register();
        var login = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<AuthTokens> rotate = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { return auth.refresh(login.refreshToken()); }
                catch (InvalidRefreshTokenException expected) { return null; }
            };
            var first = executor.submit(rotate);
            var second = executor.submit(rotate);
            AuthTokens a = first.get(15, TimeUnit.SECONDS);
            AuthTokens b = second.get(15, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null), "Exactly one refresh may succeed");
            AuthTokens winner = a == null ? b : a;
            assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(winner.refreshToken()));
        }
    }

    @Test
    void replayDoesNotRevokeOtherLoginsAndSessionExpiryIsAbsolute() {
        User user = register();
        var first = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        var otherSession = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        var rotated = auth.refresh(first.refreshToken());
        // PostgreSQL timestamp precision is microseconds.
        assertEquals(first.refreshExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                rotated.refreshExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(first.refreshToken()));
        assertDoesNotThrow(() -> auth.refresh(otherSession.refreshToken()));
        jdbc.update("update auth_refresh_sessions set expires_at=? where user_id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.getId());
        var current = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        jdbc.update("update auth_refresh_sessions set expires_at=? where user_id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.getId());
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(current.refreshToken()));
    }

    @Test
    void simultaneousRegistrationCreatesOnlyOneUser() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> register = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { auth.register(new RegisterRequest(email, PASSWORD)); return true; }
                catch (BusinessException expected) {
                    assertEquals("EMAIL_ALREADY_EXISTS", expected.getErrorCode().name());
                    return false;
                }
            };
            var a = executor.submit(register);
            var b = executor.submit(register);
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            users.findByEmailIgnoreCase(email).ifPresent(user -> createdUsers.add(user.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login", "refresh", "logout"})
    void allAuthWritesRequireCsrf(String endpoint) throws Exception {
        mvc.perform(post("/api/auth/" + endpoint)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void csrfBodyWorksAcrossOriginsAndCorsRejectsUntrustedOrigin() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).path("data").path("token").asString();
        assertEquals(response.getCookie("XSRF-TOKEN").getValue(), token);
        mvc.perform(post("/api/auth/logout").cookie(response.getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token).header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk());
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-XSRF-TOKEN,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void ordinaryUserCannotChangeOtherUsersEmail() throws Exception {
        User user = register();
        var login = auth.login(new LoginRequest(user.getEmail(), PASSWORD));
        mvc.perform(withCsrf(put("/api/v1/users/" + user.getId())).cookie(new Cookie("access_token", login.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"changed\",\"displayName\":\"Changed\",\"email\":\"changed@example.com\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "                                ", "á"})
    void invalidRegistrationPasswordIsRejected(String password) throws Exception {
        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("valid@example.com", password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unicodePasswordByteLimitAndInvalidEmailAreValidated() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("valid@example.com", "á".repeat(37))))
                .andExpect(status().isBadRequest());
        mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("invalid", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    private User register() {
        var dto = auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com", PASSWORD));
        createdUsers.add(dto.id());
        return users.findById(dto.id()).orElseThrow();
    }

    private void assertLoginDenied(String email, String password) throws Exception {
        mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private String credentials(String email, String password) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        return request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
    }
}
