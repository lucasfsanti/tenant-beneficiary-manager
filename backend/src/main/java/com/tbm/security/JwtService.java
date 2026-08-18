package com.tbm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_TENANT_IDS = "tenantIds";

    private final Key signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String issueToken(UUID userId, String username, List<UUID> tenantIds) {
        Instant now = Instant.now();
        List<String> tenantIdStrings = tenantIds.stream().map(UUID::toString).collect(Collectors.toList());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim(CLAIM_TENANT_IDS, tenantIdStrings)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        List<String> tenantIdStrings = claims.get(CLAIM_TENANT_IDS, List.class);
        List<UUID> tenantIds = tenantIdStrings.stream().map(UUID::fromString).collect(Collectors.toList());
        return new JwtPrincipal(userId, username, tenantIds);
    }

    public record JwtPrincipal(UUID userId, String username, List<UUID> tenantIds) {
    }
}
