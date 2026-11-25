package com.gharpahucho.security;

import com.gharpahucho.config.GharPahuchoProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(GharPahuchoProperties props) {
        byte[] secret = props.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("gharpahucho.security.jwt-secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlMinutes = props.getSecurity().getJwtTtlMinutes();
    }

    public String issue(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthPrincipal> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
