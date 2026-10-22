package app.api.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;
    private final Duration expiration;

    /** Rejects missing configuration, invalid Base64, and keys shorter than 256 bits. */
    public JwtUtil(@Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.expiration}") Duration expiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET is required");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64");
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 decoded bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (expiration == null || expiration.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("JWT expiration must be at least one second");
        }
        this.issuer = issuer;
        this.expiration = expiration;
        this.algorithm = Algorithm.HMAC256(key);
        this.verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withClaim("sub", (claim, jwt) -> claim.asString() != null && !claim.asString().isBlank())
                .withClaimPresence("exp")
                .build();
    }

    /** Signs an access token with the user ID as subject and the configured lifetime. */
    public String generateToken(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be blank");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return JWT.create().withSubject(userId).withIssuer(issuer)
                .withIssuedAt(now).withExpiresAt(now.plus(expiration)).sign(algorithm);
    }

    /** Returns the verified subject; invalid or expired tokens throw JWTVerificationException. */
    public String extractUserId(String token) {
        return verify(token).getSubject();
    }

    /** Returns expiration in UTC; invalid or expired tokens throw JWTVerificationException. */
    public Instant extractExpiration(String token) {
        return verify(token).getExpiresAtAsInstant();
    }

    /** Checks expiration after signature and issuer verification; other invalid tokens throw. */
    public boolean isTokenExpired(String token) {
        try {
            verify(token);
            return false;
        } catch (TokenExpiredException exception) {
            return true;
        }
    }

    /** Returns false for invalid tokens, including expired, tampered, null, or blank input. */
    public boolean validateToken(String token) {
        try {
            verify(token);
            return true;
        } catch (JWTVerificationException exception) {
            return false;
        }
    }

    private DecodedJWT verify(String token) {
        if (token == null || token.isBlank()) {
            throw new JWTVerificationException("Token must not be blank");
        }
        return verifier.verify(token);
    }
}
