package app.api.config;

import java.util.List;
import app.api.auth.config.AuthCookieProperties;
import app.api.user.repository.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import app.api.dto.response.ApiErrorResponse;
import app.api.exception.ErrorCode;
import app.api.security.jwt.JwtAuthenticationFilter;
import app.api.security.jwt.JwtUtil;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthCookieProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler,
            JwtUtil jwtUtil, UserRepository users, AuthCookieProperties cookieProperties,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        // Cookie CSRF cho JavaScript đọc; cookie JWT phải được bên phát hành đặt HttpOnly.
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> {
            cookie.path("/").secure(cookieProperties.secure()).sameSite(cookieProperties.sameSite());
            if (cookieProperties.domain() != null) cookie.domain(cookieProperties.domain());
        });
        return http
                // Browser tự gửi JWT cookie, vì vậy các request thay đổi dữ liệu vẫn cần CSRF token.
                .csrf(csrf -> csrf
                        .spa()

                        // Nơi Spring Security lưu và đọc CSRF token
                        .csrfTokenRepository(csrfRepository)

                        // Chỉ bỏ qua CSRF cho hai API này
                        .ignoringRequestMatchers(request -> {
                                String path = request.getServletPath();
                                String method = request.getMethod();

                                return "POST".equals(method)
                                        && ("/api/auth/register".equals(path)
                                            || "/api/auth/login".equals(path));
                            })
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/swagger-ui/**").permitAll()
                        // API quản trị hồ sơ cũ cho phép đổi email của bất kỳ user: giới hạn ADMIN.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/v3/api-docs",
                            "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                // Không khai báo filter là Servlet bean: chỉ chạy một lần trong security chain.
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, users), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, exception) -> {
            response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of(ErrorCode.UNAUTHORIZED, request.getRequestURI()));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, exception) -> {
            response.setStatus(ErrorCode.ACCESS_DENIED.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of(ErrorCode.ACCESS_DENIED, request.getRequestURI()));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${auth.cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins) {
        if (allowedOrigins.isEmpty() || allowedOrigins.stream().anyMatch(origin ->
                origin.contains("*") || !origin.matches("https?://[^/]+"))) {
            throw new IllegalArgumentException("CORS requires explicit HTTP(S) origins without wildcard or path");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }
}
