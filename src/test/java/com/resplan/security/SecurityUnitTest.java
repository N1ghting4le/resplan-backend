package com.resplan.security;

import com.resplan.domain.AppUser;
import com.resplan.domain.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.resplan.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("UC-4 Маркеры доступа JWT: JwtTokenService, JwtAuthenticationFilter")
class SecurityUnitTest {

    private static final String SECRET = "tkOAeMwRVKARIkV4J2ROzbQVY75tblnnvl5/7loX1AI=";
    private static final Instant NOW = Instant.parse("2026-10-05T06:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, Duration.ofHours(8), "resplan");

    private static JwtTokenService at(Instant instant) {
        return new JwtTokenService(PROPERTIES, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("JwtTokenService")
    class Tokens {

        private final AppUser rm = user(3, UserRole.RM);

        @Test
        @DisplayName("FR4-2, FR4-3: маркер содержит логин и роль, срок действия – 8 часов")
        void issuedTokenIsVerified() {
            IssuedToken token = at(NOW).issue(rm);

            assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
            assertThat(token.value().split("\\.")).hasSize(3);
            assertThat(at(NOW.plusSeconds(3600)).verify(token.value()))
                    .contains(new TokenClaims("rm3", UserRole.RM));
        }

        @Test
        @DisplayName("FR4-3: просроченный маркер отклоняется")
        void expiredTokenIsRejected() {
            IssuedToken token = at(NOW).issue(rm);

            assertThat(at(NOW.plus(Duration.ofHours(8)).plusSeconds(1)).verify(token.value())).isEmpty();
        }

        @Test
        @DisplayName("FR4-3: поддельный маркер (изменена подпись или полезная нагрузка) отклоняется")
        void tamperedTokenIsRejected() {
            String token = at(NOW).issue(rm).value();
            String[] parts = token.split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"iss\":\"resplan\",\"sub\":\"rm3\",\"role\":\"PM\"}".getBytes());

            assertThat(at(NOW).verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
            assertThat(at(NOW).verify(token.substring(0, token.length() - 2) + "xx")).isEmpty();
            assertThat(at(NOW).verify("not-a-jwt")).isEmpty();
            assertThat(at(NOW).verify("")).isEmpty();
        }

        @Test
        @DisplayName("FR4-3: маркер другого издателя отклоняется")
        void foreignIssuerIsRejected() {
            JwtTokenService foreign = new JwtTokenService(new JwtProperties(SECRET, Duration.ofHours(1), "other"),
                    Clock.fixed(NOW, ZoneOffset.UTC));

            assertThat(at(NOW).verify(foreign.issue(rm).value())).isEmpty();
        }
    }

    @Nested
    @DisplayName("JwtAuthenticationFilter")
    class Filter {

        private final TokenVerifier verifier = mock(TokenVerifier.class);
        private final FilterChain chain = mock(FilterChain.class);
        private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        private final MockHttpServletResponse response = new MockHttpServletResponse();

        @BeforeEach
        @AfterEach
        void clearContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("FR4-2: действительный маркер – пользователь и роль ROLE_PM в контексте безопасности")
        void validBearerTokenAuthenticates() throws Exception {
            request.addHeader("Authorization", "Bearer good");
            when(verifier.verify("good")).thenReturn(Optional.of(new TokenClaims("pm", UserRole.PM)));

            new JwtAuthenticationFilter(verifier).doFilter(request, response, chain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getPrincipal()).isEqualTo(new TokenClaims("pm", UserRole.PM));
            assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PM");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("FR4-4: без маркера или с недействительным маркером запрос остается анонимным")
        void missingOrInvalidTokenLeavesAnonymous() throws Exception {
            when(verifier.verify("bad")).thenReturn(Optional.empty());
            MockHttpServletRequest basic = new MockHttpServletRequest("GET", "/api/projects");
            basic.addHeader("Authorization", "Basic cG06cmVzcGxhbg==");
            request.addHeader("Authorization", "Bearer bad");

            new JwtAuthenticationFilter(verifier).doFilter(new MockHttpServletRequest(), response, chain);
            new JwtAuthenticationFilter(verifier).doFilter(basic, response, chain);
            new JwtAuthenticationFilter(verifier).doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(verifier, times(1)).verify(anyString());
            verify(chain, times(3)).doFilter(any(), any());
        }
    }
}
