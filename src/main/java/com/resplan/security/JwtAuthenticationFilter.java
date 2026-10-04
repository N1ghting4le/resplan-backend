package com.resplan.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Извлекает маркер из заголовка «Authorization: Bearer» и помещает пользователя
 * в контекст безопасности. Сервер не хранит сеансы (принцип REST «без сохранения состояния»).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final TokenVerifier verifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            verifier.verify(header.substring(BEARER.length())).ifPresent(claims -> {
                var authority = new SimpleGrantedAuthority("ROLE_" + claims.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(claims, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
