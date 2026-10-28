package com.authbase.api.dto;

import com.authbase.domain.AppSettings;

import java.time.Instant;

public record SettingsResponse(
        boolean allowPublicRegistration,
        int maxFailedAttempts,
        int lockDurationMinutes,
        int passwordMinLength,
        int passwordMaxLength,
        boolean passwordRequireUppercase,
        boolean passwordRequireLowercase,
        boolean passwordRequireDigit,
        Instant updatedAt
) {
    public static SettingsResponse from(AppSettings settings) {
        return new SettingsResponse(
                settings.allowPublicRegistration,
                settings.maxFailedAttempts,
                settings.lockDurationMinutes,
                settings.passwordMinLength,
                settings.passwordMaxLength,
                settings.passwordRequireUppercase,
                settings.passwordRequireLowercase,
                settings.passwordRequireDigit,
                settings.updatedAt
        );
    }
}
