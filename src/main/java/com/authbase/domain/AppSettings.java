package com.authbase.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSettings extends PanacheEntityBase {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id = DEFAULT_ID;

    @Column(name = "allow_public_registration", nullable = false)
    public boolean allowPublicRegistration = true;

    @Column(name = "max_failed_attempts", nullable = false)
    public int maxFailedAttempts = 5;

    @Column(name = "lock_duration_minutes", nullable = false)
    public int lockDurationMinutes = 15;

    @Column(name = "password_min_length", nullable = false)
    public int passwordMinLength = 8;

    @Column(name = "password_max_length", nullable = false)
    public int passwordMaxLength = 72;

    @Column(name = "password_require_uppercase", nullable = false)
    public boolean passwordRequireUppercase = true;

    @Column(name = "password_require_lowercase", nullable = false)
    public boolean passwordRequireLowercase = true;

    @Column(name = "password_require_digit", nullable = false)
    public boolean passwordRequireDigit = true;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null || id.isBlank()) {
            id = DEFAULT_ID;
        }
        applyDefaultsIfNeeded();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void applyDefaultsIfNeeded() {
        if (maxFailedAttempts < 1) {
            maxFailedAttempts = 5;
        }
        if (lockDurationMinutes < 1) {
            lockDurationMinutes = 15;
        }
        if (passwordMinLength < 1) {
            passwordMinLength = 8;
            passwordMaxLength = 72;
            passwordRequireUppercase = true;
            passwordRequireLowercase = true;
            passwordRequireDigit = true;
        }
        if (passwordMaxLength < passwordMinLength) {
            passwordMaxLength = Math.max(passwordMinLength, 72);
        }
    }

    public static AppSettings current() {
        AppSettings settings = findById(DEFAULT_ID);
        if (settings == null) {
            throw new IllegalStateException("App settings not initialized");
        }
        return settings;
    }
}
