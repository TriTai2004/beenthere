package app.api.auth.service;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class LocalUserDetails extends User {
    private final UUID userId;

    public LocalUserDetails(UUID userId, String email, String password, String role, boolean enabled) {
        super(email, password, enabled, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
}
