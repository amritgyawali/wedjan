package com.wedjan.api.auth;

import com.wedjan.api.config.WedjanProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenTtlSeconds;

    public JwtService(WedjanProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = properties.auth().accessTokenTtlSeconds();
    }

    public record AccessTokenClaims(UUID accountId, String email, List<String> roles) {}

    public String generateAccessToken(UUID accountId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    /** Returns claims for a valid, unexpired token; null otherwise (never throws). */
    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            return new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    roles == null ? List.of() : roles);
        } catch (Exception e) {
            return null;
        }
    }
}
