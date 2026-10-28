package com.authbase.service;

import com.authbase.domain.AppSettings;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SettingsBootstrap {

    private static final Logger LOG = Logger.getLogger(SettingsBootstrap.class);

    @Transactional
    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE + 20) StartupEvent event) {
        AppSettings settings = AppSettings.findById(AppSettings.DEFAULT_ID);
        if (settings == null) {
            settings = new AppSettings();
            settings.id = AppSettings.DEFAULT_ID;
            settings.allowPublicRegistration = true;
            settings.maxFailedAttempts = 5;
            settings.lockDurationMinutes = 15;
            settings.passwordMinLength = 8;
            settings.passwordMaxLength = 72;
            settings.passwordRequireUppercase = true;
            settings.passwordRequireLowercase = true;
            settings.passwordRequireDigit = true;
            settings.persist();
            LOG.info("Configuración general inicial creada");
            return;
        }
        settings.applyDefaultsIfNeeded();
    }
}
