package app.api.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private final byte[] key = new SecureRandom().generateSeed(32);
    private final String secret = Base64.getEncoder().encodeToString(key);
    private final JwtUtil util = new JwtUtil(secret, "beenthere", Duration.ofMinutes(15));

    @Test
    void generatedTokenHasVerifiedSubjectIssuerAndConfiguredLifetime() {
        String token = util.generateToken("user-123");
        assertTrue(util.validateToken(token));
        assertFalse(util.isTokenExpired(token));
        assertEquals("user-123", util.extractUserId(token));
        var verified = JWT.require(Algorithm.HMAC256(key)).withIssuer("beenthere").build().verify(token);
        assertEquals(Duration.ofMinutes(15), Duration.between(verified.getIssuedAtAsInstant(), util.extractExpiration(token)));
    }

    @Test
    void expiredTokenIsRejectedAndClaimsAreNotReturned() {
        String token = JWT.create().withIssuer("beenthere").withSubject("user-123")
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(key));
        assertFalse(util.validateToken(token));
        assertTrue(util.isTokenExpired(token));
        assertThrows(JWTVerificationException.class, () -> util.extractUserId(token));
        assertThrows(JWTVerificationException.class, () -> util.extractExpiration(token));
    }

    @Test
    void wrongIssuerAndSignatureAreRejectedEvenWhenExpired() {
        for (Instant expiration : new Instant[] {Instant.now().plusSeconds(60), Instant.now().minusSeconds(60)}) {
            String wrongIssuer = JWT.create().withIssuer("other").withSubject("user")
                    .withExpiresAt(expiration).sign(Algorithm.HMAC256(key));
            String wrongSignature = JWT.create().withIssuer("beenthere").withSubject("user")
                    .withExpiresAt(expiration).sign(Algorithm.HMAC256(new SecureRandom().generateSeed(32)));
            for (String token : new String[] {wrongIssuer, wrongSignature}) {
                assertFalse(util.validateToken(token));
                assertThrows(JWTVerificationException.class, () -> util.extractUserId(token));
                assertThrows(JWTVerificationException.class, () -> util.isTokenExpired(token));
            }
        }
    }

    @Test
    void missingClaimsAndWrongAlgorithmAreRejected() {
        assertFalse(util.validateToken(JWT.create().withIssuer("beenthere").withSubject("user")
                .sign(Algorithm.HMAC256(key))));
        assertFalse(util.validateToken(JWT.create().withIssuer("beenthere")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(key))));
        assertFalse(util.validateToken(JWT.create().withIssuer("beenthere").withSubject("user")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.none())));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-token", "a.b.c"})
    void malformedInputIsInvalid(String token) {
        assertFalse(util.validateToken(token));
        assertThrows(JWTVerificationException.class, () -> util.extractExpiration(token));
    }

    @Test
    void invalidConfigurationAndBlankSubjectsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("", "issuer", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("not base64!", "issuer", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(Base64.getEncoder().encodeToString(new byte[31]), "issuer", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(secret, " ", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(secret, "issuer", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> util.generateToken(" "));
    }
}
