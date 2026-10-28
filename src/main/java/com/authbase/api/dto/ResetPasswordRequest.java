package com.authbase.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "El token es obligatorio")
        @Size(min = 20, max = 200, message = "El token no es válido")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 1, max = 72, message = "La contraseña debe tener entre 1 y 72 caracteres")
        String newPassword
) {
}
