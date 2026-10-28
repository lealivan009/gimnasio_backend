package com.authbase.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordHasher {

    /**
     * Hash dummy para igualar el tiempo de respuesta cuando el email no existe.
     */
    private static final String DUMMY_HASH = BcryptUtil.bcryptHash("not-a-real-password-dummy");

    public String hash(String rawPassword) {
        return BcryptUtil.bcryptHash(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return BcryptUtil.matches(rawPassword, passwordHash);
    }

    public void matchesDummy(String rawPassword) {
        BcryptUtil.matches(rawPassword, DUMMY_HASH);
    }
}
