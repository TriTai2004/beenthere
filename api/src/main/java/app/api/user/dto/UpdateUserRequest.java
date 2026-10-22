package app.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT replaces the profile; null optional fields clear their existing values. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 40) String username,
        @NotBlank @Size(max = 100) String displayName,
        @Email @Size(max = 255) String email,
        String bio,
        String avatarKey,
        String coverKey) {
}
