package app.api.support;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {
    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry properties) {
        String key = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));
        properties.add("jwt.secret", () -> key);
        properties.add("jwt.issuer", () -> "auth-integration-test");
    }
}
