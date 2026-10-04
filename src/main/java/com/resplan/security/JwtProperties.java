package com.resplan.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Параметры JWT из application.yml: секрет HMAC-SHA256 (Base64, не менее 256 бит), срок действия, издатель */
@ConfigurationProperties(prefix = "resplan.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {
}
