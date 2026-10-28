package com.authbase.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSettingsRequest(
        @NotNull(message = "allowPublicRegistration es obligatorio")
        Boolean allowPublicRegistration,

        @NotNull(message = "maxFailedAttempts es obligatorio")
        @Min(value = 1, message = "Los reintentos deben ser al menos 1")
        @Max(value = 20, message = "Los reintentos no pueden ser más de 20")
        Integer maxFailedAttempts,

        @NotNull(message = "lockDurationMinutes es obligatorio")
        @Min(value = 1, message = "El bloqueo debe ser al menos 1 minuto")
        @Max(value = 1440, message = "El bloqueo no puede superar 1440 minutos")
        Integer lockDurationMinutes,

        @NotNull(message = "passwordMinLength es obligatorio")
        @Min(value = 6, message = "El mínimo de contraseña debe ser al menos 6")
        @Max(value = 32, message = "El mínimo de contraseña no puede superar 32")
        Integer passwordMinLength,

        @NotNull(message = "passwordMaxLength es obligatorio")
        @Min(value = 8, message = "El máximo de contraseña debe ser al menos 8")
        @Max(value = 72, message = "El máximo de contraseña no puede superar 72")
        Integer passwordMaxLength,

        @NotNull(message = "passwordRequireUppercase es obligatorio")
        Boolean passwordRequireUppercase,

        @NotNull(message = "passwordRequireLowercase es obligatorio")
        Boolean passwordRequireLowercase,

        @NotNull(message = "passwordRequireDigit es obligatorio")
        Boolean passwordRequireDigit
) {
}
