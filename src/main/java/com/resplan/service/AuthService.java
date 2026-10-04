package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.error.AuthenticationFailedException;
import com.resplan.repository.AppUserRepository;
import com.resplan.security.IssuedToken;
import com.resplan.security.TokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** UC-4: вход в систему по логину и паролю с выдачей маркера доступа */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public record LoginResult(AppUser user, IssuedToken token) {
    }

    public LoginResult login(String login, String password) {
        AppUser user = users.findByLogin(login.trim().toLowerCase())
                .filter(AppUser::isActive)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new AuthenticationFailedException("Неверные учетные данные"));
        user.recordLogin(clock.instant());
        return new LoginResult(user, tokenIssuer.issue(user));
    }
}
