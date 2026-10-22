package app.api.auth;

import app.api.auth.dto.*;
import app.api.auth.repository.*;
import app.api.auth.service.AuthService;
import app.api.exception.*;
import app.api.security.jwt.JwtUtil;
import app.api.user.entity.User;
import app.api.user.mapper.UserMapper;
import app.api.user.repository.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final AuthService auth = new AuthService(users, new UserMapper(), manager, passwords,
            mock(JwtUtil.class), sessions, tokens, Duration.ofDays(30));

    @Test
    void duplicateEmailDoesNotEncodeOrSave() {
        when(users.existsByEmailIgnoreCase("tri@example.com")).thenReturn(true);
        var error = assertThrows(BusinessException.class,
                () -> auth.register(new RegisterRequest(" TRI@EXAMPLE.COM ", "StrongPassword123!")));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, error.getErrorCode());
        verifyNoInteractions(passwords, sessions, tokens);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedDatabaseFailuresAreNotReportedAsDuplicateEmail() {
        var error = new DataIntegrityViolationException("unrelated constraint");
        when(users.saveAndFlush(any(User.class))).thenThrow(error);
        assertSame(error, assertThrows(DataIntegrityViolationException.class,
                () -> auth.register(new RegisterRequest("tri@example.com", "StrongPassword123!"))));
    }

    @Test
    void authenticationInfrastructureFailuresAreNotReportedAsBadCredentials() {
        var error = new InternalAuthenticationServiceException("database unavailable");
        when(manager.authenticate(any())).thenThrow(error);
        assertSame(error, assertThrows(InternalAuthenticationServiceException.class,
                () -> auth.login(new LoginRequest("tri@example.com", "StrongPassword123!"))));
        verifyNoInteractions(sessions, tokens);
    }

    @Test
    void invalidCredentialsHaveGenericMessageAndDoNotCreateSession() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("private detail"));
        var error = assertThrows(BusinessException.class,
                () -> auth.login(new LoginRequest("tri@example.com", "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
        assertEquals("Invalid email or password", error.getMessage());
        verifyNoInteractions(sessions, tokens);
    }

    @Test
    void missingMalformedLogoutDoesNotAccessDatabase() {
        auth.logout(null);
        auth.logout("");
        auth.logout("malformed");
        verifyNoInteractions(sessions, tokens);
    }
}
