package app.api.config;

import app.api.user.entity.User;
import app.api.user.enums.UserRole;
import app.api.user.enums.UserStatus;
import app.api.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDetailsConfigTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserDetailsService service = new UserDetailsConfig(repository);

    @Test
    void loadsStoredCredentialsAndRole() {
        User user = storedUser();
        user.setRole(UserRole.ADMIN);
        when(repository.findByEmailIgnoreCase("TRI@example.com")).thenReturn(Optional.of(user));
        var details = service.loadUserByUsername(" TRI@example.com ");
        assertEquals("tri@example.com", details.getUsername());
        assertEquals(user.getPasswordHash(), details.getPassword());
        assertTrue(details.isEnabled());
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
        verify(repository).findByEmailIgnoreCase("TRI@example.com");
    }

    @Test
    void missingUserThrowsSecurityException() {
        when(repository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void invalidEmailDoesNotQueryDatabase(String email) {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(email));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BANNED"})
    void inactiveAccountsAreDisabled(UserStatus status) {
        User user = storedUser();
        user.setStatus(status);
        when(repository.findByEmailIgnoreCase("tri@example.com")).thenReturn(Optional.of(user));
        assertFalse(service.loadUserByUsername("tri@example.com").isEnabled());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void passwordlessProfilesAreDisabled(String password) {
        User user = storedUser();
        user.setPasswordHash(password);
        when(repository.findByEmailIgnoreCase("tri@example.com")).thenReturn(Optional.of(user));
        var details = service.loadUserByUsername("tri@example.com");
        assertFalse(details.isEnabled());
        assertEquals("", details.getPassword());
    }

    private User storedUser() {
        var user = new User();
        user.setUsername("tri");
        user.setEmail("tri@example.com");
        user.setPasswordHash("stored-hash");
        return user;
    }
}
