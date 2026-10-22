package app.api.auth.dto;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
    @JsonIgnore
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override public String toString() { return "LoginRequest[redacted]"; }
}
