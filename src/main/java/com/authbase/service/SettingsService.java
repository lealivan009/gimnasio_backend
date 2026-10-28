package com.authbase.service;

import com.authbase.api.dto.SettingsResponse;
import com.authbase.api.dto.UpdateSettingsRequest;
import com.authbase.domain.AppSettings;
import com.authbase.error.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SettingsService {

    public AppSettings current() {
        AppSettings settings = AppSettings.current();
        settings.applyDefaultsIfNeeded();
        return settings;
    }

    public SettingsResponse get() {
        return SettingsResponse.from(current());
    }

    @Transactional
    public SettingsResponse update(UpdateSettingsRequest request) {
        if (request.passwordMaxLength() < request.passwordMinLength()) {
            throw ApiException.badRequest(
                    "INVALID_PASSWORD_POLICY",
                    "El máximo de contraseña no puede ser menor que el mínimo"
            );
        }
        AppSettings settings = current();
        settings.allowPublicRegistration = request.allowPublicRegistration();
        settings.maxFailedAttempts = request.maxFailedAttempts();
        settings.lockDurationMinutes = request.lockDurationMinutes();
        settings.passwordMinLength = request.passwordMinLength();
        settings.passwordMaxLength = request.passwordMaxLength();
        settings.passwordRequireUppercase = request.passwordRequireUppercase();
        settings.passwordRequireLowercase = request.passwordRequireLowercase();
        settings.passwordRequireDigit = request.passwordRequireDigit();
        return SettingsResponse.from(settings);
    }
}
