package app.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.cookie")
public record AuthCookieProperties(boolean secure, String sameSite, String domain) {
    public AuthCookieProperties {
        sameSite = sameSite == null ? "Lax" : sameSite;
        if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite)) {
            throw new IllegalArgumentException("Cookie SameSite must be Lax, Strict or None");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");
        }
        domain = domain == null || domain.isBlank() ? null : domain.strip();
    }
}
