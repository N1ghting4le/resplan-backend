package com.resplan.security;

import com.resplan.domain.AppUser;
import com.resplan.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Реализация маркеров доступа в формате JWT (RFC 7519) с подписью HS256 */
@Service
public class JwtTokenService implements TokenIssuer, TokenVerifier {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey key;
    private final Clock clock;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());
        String token = Jwts.builder()
                .setIssuer(properties.issuer())
                .setSubject(user.getLogin())
                .claim(ROLE_CLAIM, user.getRole().name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public Optional<TokenClaims> verify(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(properties.issuer())
                    .setClock(() -> Date.from(clock.instant()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            return Optional.of(new TokenClaims(claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
