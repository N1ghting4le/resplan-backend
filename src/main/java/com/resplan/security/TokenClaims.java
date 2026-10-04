package com.resplan.security;

import com.resplan.domain.UserRole;

/** Сведения о пользователе, извлеченные из маркера: логин и роль */
public record TokenClaims(String login, UserRole role) {
}
