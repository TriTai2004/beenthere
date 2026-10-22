package app.api.auth;

import app.api.auth.config.AuthCookieProperties;
import app.api.auth.service.AuthTokens;
import app.api.auth.web.AuthCookies;
import app.api.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class AuthCookiesTest {
    @Test
    void secureCookieCreationAndDeletionUseSameDomainPathAndFlags() {
        var cookies = new AuthCookies(new AuthCookieProperties(true, "None", "example.com"));
        var response = new MockHttpServletResponse();
        cookies.write(response, new AuthTokens(null, "access", "refresh", Instant.now().plusSeconds(900),
                Instant.now().plusSeconds(3600)));
        for (String name : List.of("access_token", "refresh_token")) {
            var cookie = response.getCookie(name);
            assertTrue(cookie.isHttpOnly());
            assertTrue(cookie.getSecure());
            assertEquals("None", cookie.getAttribute("SameSite"));
            assertEquals("example.com", cookie.getDomain());
        }
        var cleared = new MockHttpServletResponse();
        cookies.clear(cleared);
        for (String name : List.of("access_token", "refresh_token")) {
            assertEquals(response.getCookie(name).getPath(), cleared.getCookie(name).getPath());
            assertEquals(response.getCookie(name).getDomain(), cleared.getCookie(name).getDomain());
            assertEquals(0, cleared.getCookie(name).getMaxAge());
        }
    }

    @Test
    void insecureSameSiteNoneAndWildcardOriginsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new AuthCookieProperties(false, "None", null));
        assertThrows(IllegalArgumentException.class, () -> new AuthCookieProperties(true, "bad", null));
        var config = new SecurityConfig();
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurationSource(List.of("*")));
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurationSource(List.of("https://*.example.com")));
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurationSource(List.of()));
    }
}
