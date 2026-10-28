package com.authbase.security;

import com.authbase.domain.AppSettings;
import com.authbase.error.ApiException;
import com.authbase.service.SettingsService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class PasswordPolicy {

    private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");

    private final SettingsService settingsService;

    public PasswordPolicy(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void validate(String password) {
        AppSettings settings = settingsService.current();
        int min = settings.passwordMinLength;
        int max = settings.passwordMaxLength;

        if (password == null || password.length() < min || password.length() > max) {
            throw ApiException.badRequest(
                    "WEAK_PASSWORD",
                    "La contraseña debe tener entre " + min + " y " + max + " caracteres"
            );
        }

        List<String> missing = new ArrayList<>();
        if (settings.passwordRequireUppercase && !UPPER.matcher(password).matches()) {
            missing.add("mayúscula");
        }
        if (settings.passwordRequireLowercase && !LOWER.matcher(password).matches()) {
            missing.add("minúscula");
        }
        if (settings.passwordRequireDigit && !DIGIT.matcher(password).matches()) {
            missing.add("un número");
        }
        if (!missing.isEmpty()) {
            throw ApiException.badRequest(
                    "WEAK_PASSWORD",
                    "La contraseña debe incluir " + String.join(", ", missing)
            );
        }
    }
}
