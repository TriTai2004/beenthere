package app.api.user.entity;

import app.api.user.enums.UserRole;
import app.api.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.Locale;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 40)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "email", nullable = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = true, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "bio", nullable = true, columnDefinition = "text")
    private String bio;

    @Column(name = "avatar_key", nullable = true, columnDefinition = "text")
    private String avatarKey;

    @Column(name = "cover_key", nullable = true, columnDefinition = "text")
    private String coverKey;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'USER'")
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified_at", nullable = true, columnDefinition = "timestamptz")
    private Instant emailVerifiedAt;

    @ColumnDefault("now()")
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @ColumnDefault("now()")
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;
}
