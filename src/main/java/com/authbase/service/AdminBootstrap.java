package com.authbase.service;

import com.authbase.config.AuthProperties;
import com.authbase.helpers.Names;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.security.PasswordHasher;
import com.authbase.security.PasswordPolicy;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AdminBootstrap {

    private static final Logger LOG = Logger.getLogger(AdminBootstrap.class);

    private final AuthProperties properties;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;

    public AdminBootstrap(AuthProperties properties, PasswordHasher passwordHasher, PasswordPolicy passwordPolicy) {
        this.properties = properties;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional
    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE + 40) StartupEvent event) {
        String email = properties.bootstrapAdminEmail();
        String password = properties.bootstrapAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (User.count() > 0) {
            return;
        }
        passwordPolicy.validate(password);
        User admin = new User();
        admin.email = User.normalizeEmail(email);
        String[] parts = Names.split(properties.bootstrapAdminName());
        admin.firstName = parts[0].isBlank() ? "Administrador" : parts[0];
        String lastName = properties.bootstrapAdminLastName();
        admin.lastName = lastName == null || lastName.isBlank()
                ? (parts[1].isBlank() ? "Admin" : parts[1])
                : lastName.trim();
        admin.passwordHash = passwordHasher.hash(password);
        admin.role = Role.ADMIN;
        admin.persist();
        LOG.infof("Usuario administrador inicial creado: %s", admin.email);
    }
}
