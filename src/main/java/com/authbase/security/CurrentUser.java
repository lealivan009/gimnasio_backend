package com.authbase.security;

import com.authbase.domain.User;
import com.authbase.error.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class CurrentUser {

    private final JsonWebToken jwt;

    public CurrentUser(JsonWebToken jwt) {
        this.jwt = jwt;
    }

    public User require() {
        String id = jwt.getSubject();
        if (id == null || id.isBlank()) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Tenés que iniciar sesión");
        }
        return User.findActiveById(id)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "La sesión no es válida"));
    }

    public User requireStaff() {
        User user = require();
        if (!user.role.isStaff()) {
            throw ApiException.forbidden("FORBIDDEN", "No tenés permiso para esta acción");
        }
        return user;
    }
}
