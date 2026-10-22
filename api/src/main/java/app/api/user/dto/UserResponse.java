package app.api.user.dto;

import java.time.Instant;
import java.util.UUID;

/** Public profile only; excludes credentials, email and moderation metadata. */
public record UserResponse(
        UUID id,
        String username,
        String displayName,
        String bio,
        String avatarKey,
        String coverKey,
        Instant createdAt,
        Instant updatedAt) {
}
