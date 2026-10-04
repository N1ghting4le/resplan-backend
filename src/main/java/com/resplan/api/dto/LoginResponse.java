package com.resplan.api.dto;

import com.resplan.service.AuthService.LoginResult;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserDto user) {

    public static LoginResponse of(LoginResult r) {
        return new LoginResponse(r.token().value(), "Bearer", r.token().expiresAt(), UserDto.of(r.user()));
    }
}
