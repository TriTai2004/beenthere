package app.api.auth.service;

import app.api.auth.dto.*;
import app.api.auth.entity.*;
import app.api.auth.exception.InvalidRefreshTokenException;
import app.api.auth.repository.*;
import app.api.exception.*;
import app.api.security.jwt.JwtUtil;
import app.api.user.dto.UserResponse;
import app.api.user.entity.User;
import app.api.user.enums.UserStatus;
import app.api.user.mapper.UserMapper;
import app.api.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserMapper mapper;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwords;
    private final JwtUtil jwt;
    private final RefreshSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final Duration refreshLifetime;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, UserMapper mapper, AuthenticationManager authenticationManager,
            PasswordEncoder passwords, JwtUtil jwt, RefreshSessionRepository sessions,
            RefreshTokenRepository tokens, @Value("${auth.refresh-expiration:30d}") Duration refreshLifetime) {
        if (refreshLifetime.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("Refresh expiration must be positive");
        }
        this.users = users;
        this.mapper = mapper;
        this.authenticationManager = authenticationManager;
        this.passwords = passwords;
        this.jwt = jwt;
        this.sessions = sessions;
        this.tokens = tokens;
        this.refreshLifetime = refreshLifetime;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setUsername("user_" + UUID.randomUUID().toString().replace("-", ""));
        user.setDisplayName("New member");
        try {
            return mapper.toResponse(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_users_auth_email".equals(constraint.getConstraintName())) {
                    throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                }
            }
            throw exception;
        }
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        UUID authenticatedUserId;
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            authenticatedUserId = ((LocalUserDetails) authentication.getPrincipal()).getUserId();
        } catch (BadCredentialsException | AccountStatusException exception) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = users.findById(authenticatedUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!active(user) || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Instant now = Instant.now();
        RefreshSession session = new RefreshSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(refreshLifetime));
        sessions.save(session);
        return issue(session, now);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String rawToken) {
        String hash = hashToken(rawToken);
        UUID sessionId = tokens.findSessionIdByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

        RefreshSession session = sessions.findLockedById(sessionId).orElseThrow(InvalidRefreshTokenException::new);
        RefreshToken token = tokens.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();
        if (session.getRevokedAt() != null || token.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now) || !token.getExpiresAt().isAfter(now)
                || !active(session.getUser())) {
            session.setRevokedAt(now);
            throw new InvalidRefreshTokenException();
        }
        token.setRevokedAt(now);
        return issue(session, now);
    }

    @Transactional
    public void logout(String rawToken) {
        if (!validTokenFormat(rawToken)) return;
        tokens.findSessionIdByTokenHash(hashToken(rawToken)).flatMap(sessions::findLockedById)
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = users.findById(userId).filter(AuthService::active)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return mapper.toResponse(user);
    }

    private AuthTokens issue(RefreshSession session, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setSession(session);
        token.setTokenHash(hashToken(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(session.getExpiresAt());
        tokens.save(token);
        String access = jwt.generateToken(session.getUser().getId().toString());
        return new AuthTokens(mapper.toResponse(session.getUser()), access, raw,
                jwt.extractExpiration(access), session.getExpiresAt());
    }

    private static boolean active(User user) { return user.getStatus() == UserStatus.ACTIVE; }

    private static boolean validTokenFormat(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{43}");
    }

    private static String hashToken(String value) {
        if (!validTokenFormat(value)) throw new InvalidRefreshTokenException();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
