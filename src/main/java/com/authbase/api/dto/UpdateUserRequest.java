package com.authbase.api.dto;

import com.authbase.domain.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
        String firstName,

        @Size(min = 2, max = 80, message = "El apellido debe tener entre 2 y 80 caracteres")
        String lastName,
        Role role,
        Boolean enabled,

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
        String postalCode,

        @DecimalMin(value = "50.0", message = "La altura tiene que ser de al menos 50 cm")
        @DecimalMax(value = "250.0", message = "La altura no puede superar 250 cm")
        BigDecimal heightCm,

        @DecimalMin(value = "20.0", message = "El peso tiene que ser de al menos 20 kg")
        @DecimalMax(value = "400.0", message = "El peso no puede superar 400 kg")
        BigDecimal weightKg,

        @Size(max = 2000, message = "Las observaciones son demasiado largas")
        String medicalNotes,

        @Size(max = 300, message = "Las especialidades son demasiado largas")
        String specialties,

        @Size(max = 2000, message = "La biografía es demasiado larga")
        String biography,

        @Size(max = 500, message = "Las redes son demasiado largas")
        String socialLinks,

        @Size(max = 1000, message = "Las certificaciones son demasiado largas")
        String certifications
) {
}
