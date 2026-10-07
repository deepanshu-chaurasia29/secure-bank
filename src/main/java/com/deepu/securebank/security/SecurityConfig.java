package com.deepu.securebank.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse res, int status,
                                   String message, String path) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        String json = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"message\":\"%s\",\"path\":\"%s\"}",
                java.time.LocalDateTime.now(), status, message, path);
        res.getWriter().write(json);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookies/forms involved
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Admin-only endpoints
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Everything else under /api/v1 needs a valid JWT
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        // No / bad token on a protected URL -> 401 in our standard JSON format
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, 401, "Please log in first", req.getRequestURI()))
                        // Logged in but wrong role (e.g. customer calling /admin) -> 403
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, 403, "You do not have permission to do this", req.getRequestURI()))
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
