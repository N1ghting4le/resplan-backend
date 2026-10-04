package com.resplan.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Clock;

/** Конфигурация Spring Security: JWT без сеансов; ролевой доступ задается аннотациями @PreAuthorize */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC = {
            "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenVerifier verifier, ObjectMapper json)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res, json,
                                HttpStatus.UNAUTHORIZED, "Маркер доступа отсутствует, недействителен или просрочен"))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, json,
                                HttpStatus.FORBIDDEN, "Операция недоступна для роли пользователя")))
                .addFilterBefore(new JwtAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper json, HttpStatus status,
                                     String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
