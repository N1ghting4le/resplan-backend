package com.resplan.security;

import com.resplan.domain.AppUser;

/** Выпуск маркера доступа после успешной аутентификации (используется только AuthService) */
public interface TokenIssuer {

    IssuedToken issue(AppUser user);
}
