package dev.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security model (see docs/DECISIONS.md D-004/D-005):
 * - Exactly one admin, authenticated by email OTP (Phase 1). Session cookie, no JWT.
 * - Everyone else is an anonymous guest identified by a signed cookie (Phase 4).
 * - /api/admin/** requires ROLE_ADMIN. Everything else is public.
 * - CSRF on, SPA mode: XSRF-TOKEN cookie readable by JS, echoed as X-XSRF-TOKEN.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .csrf(csrf -> csrf.spa())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())   // Phase 1 adds POST /api/auth/logout
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; "
                    + "frame-src https://www.youtube-nocookie.com; object-src 'none'; base-uri 'self'; "
                    + "frame-ancestors 'none'")));
        return http.build();
    }
}
