package app.api.security.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import app.api.user.entity.User;
import app.api.user.repository.UserRepository;
import app.api.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, users);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void noCookieIgnoresBearerHeaderAndContinues() throws Exception {
        request.addHeader("Authorization", "Bearer ignored");
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil);
        verify(chain).doFilter(request, response);
    }

    @Test
    void validCookieCreatesAuthenticationWithCurrentRoleAndWithoutCredentials() throws Exception {
        request.setCookies(new Cookie("other", "ignored"), new Cookie("access_token", "valid"));
        var user = new User();
        user.setId(UUID.randomUUID());
        when(jwtUtil.extractUserId("valid")).thenReturn(user.getId().toString());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        filter.doFilter(request, response, chain);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertEquals(user.getId().toString(), authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
        verify(jwtUtil).extractUserId("valid");
        verify(chain).doFilter(request, response);
    }

    @Test
    void existingAuthenticationIsPreserved() throws Exception {
        var existing = UsernamePasswordAuthenticationToken.authenticated("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.setCookies(new Cookie("access_token", "invalid"));
        filter.doFilter(request, response, chain);
        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil);
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenContinuesWithoutAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", "invalid"));
        when(jwtUtil.extractUserId("invalid")).thenThrow(new JWTVerificationException("private detail"));
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("", response.getContentAsString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void duplicateCookiesAreRejected() throws Exception {
        request.setCookies(new Cookie("access_token", "one"), new Cookie("access_token", "two"));
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil);
        verify(chain).doFilter(request, response);
    }

    @Test
    void inactiveDeletedAndMalformedSubjectsCannotAuthenticate() throws Exception {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.BANNED);
        request.setCookies(new Cookie("access_token", "valid"));
        when(jwtUtil.extractUserId("valid")).thenReturn(user.getId().toString());
        when(users.findById(user.getId())).thenReturn(Optional.of(user), Optional.empty());
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        when(jwtUtil.extractUserId("valid")).thenReturn("not-a-uuid");
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void downstreamExceptionsAreNotSwallowed() throws Exception {
        doThrow(new ServletException("downstream")).when(chain).doFilter(request, response);
        assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));
        verify(chain).doFilter(request, response);
    }
}
