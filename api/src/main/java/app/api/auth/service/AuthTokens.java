package app.api.auth.service;

import app.api.user.dto.UserResponse;
import java.time.Instant;

public record AuthTokens(UserResponse user, String accessToken, String refreshToken,
        Instant accessExpiresAt, Instant refreshExpiresAt) {
    @Override public String toString() { return "AuthTokens[redacted]"; }
}
