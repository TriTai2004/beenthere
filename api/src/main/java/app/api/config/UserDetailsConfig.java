package app.api.config;

import app.api.user.enums.UserStatus;
import app.api.user.repository.UserRepository;
import org.springframework.context.annotation.Configuration;
import app.api.auth.service.LocalUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class UserDetailsConfig implements UserDetailsService{

    private final UserRepository repository;

    public UserDetailsConfig(UserRepository repository) {
        this.repository = repository;
    }
 

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email must not be blank");
        }

        var user = repository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String password = user.getPasswordHash();
        boolean hasPassword = password != null && !password.isBlank();

        return new LocalUserDetails(user.getId(), user.getEmail(), hasPassword ? password : "",
                user.getRole().name(), user.getStatus() == UserStatus.ACTIVE && hasPassword);
    }
}
