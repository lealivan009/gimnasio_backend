package com.authbase.security;

import com.authbase.config.AuthProperties;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class JwtSecretGuard {

    private static final Logger LOG = Logger.getLogger(JwtSecretGuard.class);
    private static final String DEV_DEFAULT = "local-dev-only-secret-key-32-chars-min";

    private final AuthProperties properties;

    public JwtSecretGuard(AuthProperties properties) {
        this.properties = properties;
    }

    void onStart(@Observes StartupEvent event) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        if (DEV_DEFAULT.equals(secret) || secret.startsWith("local-dev-only")) {
            LOG.warn("Usando JWT_SECRET de desarrollo. Cambialo antes de desplegar.");
            if (System.getenv("RAILWAY_ENVIRONMENT") != null) {
                throw new IllegalStateException("En Railway JWT_SECRET no puede ser el valor de desarrollo. Definí un secreto fuerte.");
            }
        }
    }
}
