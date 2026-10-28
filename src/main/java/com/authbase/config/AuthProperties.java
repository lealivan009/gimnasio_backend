package com.authbase.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "app.auth")
public interface AuthProperties {

    @WithDefault("auth-base")
    String issuer();

    @WithDefault("PT15M")
    Duration accessTokenTtl();

    @WithDefault("P14D")
    Duration refreshTokenTtl();

    @WithDefault("5")
    int maxFailedAttempts();

    @WithDefault("PT15M")
    Duration lockDuration();

    String jwtSecret();

    @WithDefault("")
    String bootstrapAdminEmail();

    @WithDefault("")
    String bootstrapAdminPassword();

    @WithDefault("Administrador")
    String bootstrapAdminName();

    @WithDefault("Admin")
    String bootstrapAdminLastName();

    @WithDefault("PT30M")
    Duration passwordResetTokenTtl();

    @WithDefault("http://localhost:5173")
    String frontendUrl();
}
