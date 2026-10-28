package com.authbase.security;

import com.authbase.config.AuthProperties;
import com.authbase.domain.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@ApplicationScoped
public class JwtIssuer {

    private final AuthProperties properties;

    public JwtIssuer(AuthProperties properties) {
        this.properties = properties;
    }

    public String issueAccessToken(User user) {
        Duration ttl = properties.accessTokenTtl();
        Instant now = Instant.now();
        return Jwt.issuer(properties.issuer())
                .subject(user.id)
                .upn(user.email)
                .groups(Set.of(user.role.name()))
                .claim("email", user.email)
                .claim("name", user.displayName())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .signWithSecret(properties.jwtSecret());
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
