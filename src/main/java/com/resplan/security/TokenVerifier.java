package com.resplan.security;

import java.util.Optional;

/** Проверка маркера доступа (используется только фильтром аутентификации) */
public interface TokenVerifier {

    /** Утверждения маркера или пустое значение, если маркер поддельный или просрочен */
    Optional<TokenClaims> verify(String token);
}
