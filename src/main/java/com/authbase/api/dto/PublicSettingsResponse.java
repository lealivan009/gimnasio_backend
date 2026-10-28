package com.authbase.api.dto;

import com.authbase.domain.AppSettings;

public record PublicSettingsResponse(
        boolean allowPublicRegistration,
        int passwordMinLength,
        int passwordMaxLength,
        boolean passwordRequireUppercase,
        boolean passwordRequireLowercase,
        boolean passwordRequireDigit
) {
    public static PublicSettingsResponse from(AppSettings settings) {
        return new PublicSettingsResponse(
                settings.allowPublicRegistration,
                settings.passwordMinLength,
                settings.passwordMaxLength,
                settings.passwordRequireUppercase,
                settings.passwordRequireLowercase,
                settings.passwordRequireDigit
        );
    }
}
