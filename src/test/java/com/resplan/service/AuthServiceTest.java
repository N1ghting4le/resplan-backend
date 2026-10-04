package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.domain.UserRole;
import com.resplan.error.AuthenticationFailedException;
import com.resplan.repository.AppUserRepository;
import com.resplan.security.IssuedToken;
import com.resplan.security.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.resplan.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-4 Войти в систему: AuthService")
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T06:30:00Z");

    @Mock
    AppUserRepository users;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    TokenIssuer tokenIssuer;

    AuthService service;
    AppUser pm;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, tokenIssuer, Clock.fixed(NOW, ZoneOffset.UTC));
        pm = user(1, UserRole.PM);
    }

    @Test
    @DisplayName("FR4-2, FR4-3: верные учетные данные – маркер с ролью, вход зафиксирован")
    void successfulLoginIssuesToken() {
        IssuedToken token = new IssuedToken("jwt", NOW.plusSeconds(8 * 3600));
        when(users.findByLogin("pm1")).thenReturn(Optional.of(pm));
        when(passwordEncoder.matches("resplan", "hash")).thenReturn(true);
        when(tokenIssuer.issue(pm)).thenReturn(token);

        AuthService.LoginResult result = service.login("  PM1 ", "resplan");

        assertThat(result.user()).isSameAs(pm);
        assertThat(result.user().getRole()).isEqualTo(UserRole.PM);
        assertThat(result.token()).isSameAs(token);
        assertThat(pm.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("FR4-4: неверный пароль – «Неверные учетные данные», маркер не выдается")
    void wrongPasswordIsRejected() {
        when(users.findByLogin("pm1")).thenReturn(Optional.of(pm));
        when(passwordEncoder.matches("qwerty", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("pm1", "qwerty"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Неверные учетные данные");
        verifyNoInteractions(tokenIssuer);
        assertThat(pm.getLastLoginAt()).isNull();
    }

    @Test
    @DisplayName("FR4-4: неизвестный логин – то же сообщение, пароль не проверяется")
    void unknownLoginIsRejected() {
        when(users.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "resplan"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Неверные учетные данные");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("FR4-4: заблокированная учетная запись не получает доступ")
    void inactiveUserIsRejected() {
        ReflectionTestUtils.setField(pm, "active", false);
        when(users.findByLogin("pm1")).thenReturn(Optional.of(pm));

        assertThatThrownBy(() -> service.login("pm1", "resplan")).isInstanceOf(AuthenticationFailedException.class);
        verifyNoInteractions(passwordEncoder, tokenIssuer);
    }
}
