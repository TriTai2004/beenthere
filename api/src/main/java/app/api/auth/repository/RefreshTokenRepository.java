package app.api.auth.repository;

import app.api.auth.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @Query("select t.session.id from RefreshToken t where t.tokenHash = :hash")
    Optional<UUID> findSessionIdByTokenHash(@Param("hash") String hash);
    Optional<RefreshToken> findByTokenHash(String hash);
}
