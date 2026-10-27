package com.staytrack.security;

import com.staytrack.config.AppProperties;
import com.staytrack.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/** Gumagawa at nagva-validate ng JWT token. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(AppProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "app.jwt.secret must be at least 32 characters long. "
                    + "Generate one with: openssl rand -hex 32");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = (long) props.getJwt().getExpirationDays() * 24L * 60L * 60L * 1000L;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("role", user.getRole().name())
            .claim("email", user.getEmail())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }

    /** Ibinabalik ang user id, error kapag invalid o expired. */
    public UUID extractUserId(String token) {
        Jws<Claims> jws = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token);
        return UUID.fromString(jws.getPayload().getSubject());
    }
}
