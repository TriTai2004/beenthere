package app.api.config;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class SecurityErrorHandlingTest {
    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void filterFailuresUseSharedEnvelope(boolean authenticated) throws Exception {
        var mapper = JsonMapper.builder().build();
        var configuration = new SecurityConfig();
        var filter = new ExceptionTranslationFilter(configuration.authenticationEntryPoint(mapper), new NullRequestCache());
        filter.setAccessDeniedHandler(configuration.accessDeniedHandler(mapper));
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authenticated
                ? new UsernamePasswordAuthenticationToken("user", "unused", authorities)
                : new AnonymousAuthenticationToken("key", "anonymous", authorities));
        var request = new MockHttpServletRequest("GET", "/protected");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            throw new AccessDeniedException("private security details");
        });
        assertEquals(authenticated ? 403 : 401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        var body = mapper.readTree(response.getContentAsString());
        assertFalse(body.get("success").asBoolean());
        assertEquals(authenticated ? "ACCESS_DENIED" : "UNAUTHORIZED", body.get("code").asString());
        assertEquals("/protected", body.get("path").asString());
        Instant.parse(body.get("timestamp").asString());
        assertFalse(response.getContentAsString().contains("private security details"));
    }
}
