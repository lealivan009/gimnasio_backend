package com.authbase.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        @Size(max = 255, message = "El email es demasiado largo")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 1, max = 72, message = "La contraseña debe tener entre 1 y 72 caracteres")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 2, max = 80, message = "El apellido debe tener entre 2 y 80 caracteres")
        String lastName,

        @Size(max = 20, message = "El documento es demasiado largo")
        String documentNumber,

        @Size(max = 30, message = "El celular es demasiado largo")
        String phone,

        @Past(message = "La fecha de nacimiento no puede ser futura")
        LocalDate birthDate,

        @Size(max = 160, message = "El domicilio es demasiado largo")
        String street,

        @Size(max = 80, message = "La ciudad es demasiado larga")
        String city,

        @Size(max = 80, message = "La provincia es demasiado larga")
        String province,

        @Size(max = 20, message = "El código postal es demasiado largo")
        String postalCode
) {
}
