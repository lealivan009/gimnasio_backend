package com.authbase.service;

import com.authbase.api.dto.AuthResponse;
import com.authbase.api.dto.ChangePasswordRequest;
import com.authbase.api.dto.ForgotPasswordRequest;
import com.authbase.api.dto.LoginRequest;
import com.authbase.api.dto.RefreshRequest;
import com.authbase.api.dto.RegisterRequest;
import com.authbase.api.dto.RegisterResponse;
import com.authbase.api.dto.ResetPasswordRequest;
import com.authbase.api.dto.UpdateProfileRequest;
import com.authbase.api.dto.UserResponse;
import com.authbase.config.AuthProperties;
import com.authbase.domain.PasswordResetToken;
import com.authbase.domain.RefreshToken;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.helpers.UserProfiles;
import com.authbase.error.ApiException;
import com.authbase.security.JwtIssuer;
import com.authbase.security.PasswordHasher;
import com.authbase.security.PasswordPolicy;
import com.authbase.security.TokenService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import com.authbase.gym.GymTimes;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class AuthService {

    private static final String FORGOT_PASSWORD_MESSAGE =
            "Si el email está registrado, te enviamos un enlace para restablecer la contraseña.";

    private final AuthProperties properties;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokenService;
    private final JwtIssuer jwtIssuer;
    private final SettingsService settingsService;
    private final MailService mailService;
    private final WeightService weightService;
    private final AttendanceService attendanceService;

    public AuthService(
            AuthProperties properties,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            TokenService tokenService,
            JwtIssuer jwtIssuer,
            SettingsService settingsService,
            MailService mailService,
            WeightService weightService,
            AttendanceService attendanceService
    ) {
        this.properties = properties;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.jwtIssuer = jwtIssuer;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.weightService = weightService;
        this.attendanceService = attendanceService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!settingsService.current().allowPublicRegistration) {
            throw ApiException.forbidden(
                    "REGISTRATION_DISABLED",
                    "El registro público está deshabilitado. Pedile a un administrador que cree tu cuenta."
            );
        }
        passwordPolicy.validate(request.password());

        String email = User.normalizeEmail(request.email());
        if (User.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Ya existe una cuenta con ese email");
        }

        User user = new User();
        user.email = email;
        user.firstName = request.firstName().trim();
        user.lastName = request.lastName().trim();
        user.passwordHash = passwordHasher.hash(request.password());
        user.role = Role.ALUMNO;
        user.approved = false;
        UserProfiles.set(
                user,
                request.documentNumber(),
                request.phone(),
                request.birthDate(),
                request.street(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        user.persist();
        return new RegisterResponse(
                "Tu cuenta quedó creada. Cuando el profesor o el administrador la apruebe, vas a poder entrar.",
                UserResponse.from(user)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Instant now = Instant.now();
        User user = User.findByEmail(request.email()).orElse(null);

        if (user == null) {
            passwordHasher.matchesDummy(request.password());
            throw invalidCredentials();
        }
        if (!user.enabled) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está deshabilitada");
        }
        if (user.isLocked(now)) {
            throw ApiException.forbidden("ACCOUNT_LOCKED", "La cuenta está bloqueada temporalmente por intentos fallidos");
        }
        if (!passwordHasher.matches(request.password(), user.passwordHash)) {
            user.failedLoginAttempts += 1;
            var settings = settingsService.current();
            if (user.failedLoginAttempts >= settings.maxFailedAttempts) {
                user.lockedUntil = now.plus(java.time.Duration.ofMinutes(settings.lockDurationMinutes));
                user.failedLoginAttempts = 0;
            }
            throw invalidCredentials();
        }
        if (!user.isApproved()) {
            throw ApiException.forbidden(
                    "CUENTA_PENDIENTE",
                    "Tu cuenta todavía no fue aprobada. Cuando el profesor o el administrador la habilite, vas a poder entrar."
            );
        }

        user.failedLoginAttempts = 0;
        user.lockedUntil = null;
        user.lastLoginAt = now;
        return issueSession(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Instant now = Instant.now();
        String hash = tokenService.hash(request.refreshToken());
        RefreshToken stored = RefreshToken.findActiveByHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "El refresh token no es válido"));

        if (!stored.isActive(now)) {
            stored.revoked = true;
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "El refresh token expiró");
        }

        User user = stored.user;
        if (user.isDeleted() || !user.enabled) {
            stored.revoked = true;
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está deshabilitada");
        }
        if (!user.isApproved()) {
            stored.revoked = true;
            throw ApiException.forbidden(
                    "CUENTA_PENDIENTE",
                    "Tu cuenta todavía no fue aprobada. Cuando el profesor o el administrador la habilite, vas a poder entrar."
            );
        }

        stored.revoked = true;
        return issueSession(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = tokenService.hash(request.refreshToken());
        RefreshToken.findActiveByHash(hash).ifPresent(token -> token.revoked = true);
    }

    @Transactional
    public void logoutAll(String userId) {
        User user = User.findById(userId);
        if (user != null) {
            RefreshToken.revokeAllForUser(user);
        }
    }

    public UserResponse me(String userId) {
        User user = requireUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.firstName() != null) {
            user.firstName = request.firstName().trim();
        }
        if (request.lastName() != null) {
            user.lastName = request.lastName().trim();
        }
        UserProfiles.set(
                user,
                request.documentNumber(),
                request.phone(),
                request.birthDate(),
                request.street(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        UserProfiles.setFitness(
                user,
                request.heightCm(),
                request.weightKg(),
                request.medicalNotes(),
                request.specialties(),
                request.biography(),
                request.socialLinks(),
                request.certifications()
        );
        if (user.role.isStudent() && attendanceService.wasPresent(user.id, GymTimes.today())) {
            weightService.record(user, user.weightKg, null);
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordHasher.matches(request.currentPassword(), user.passwordHash)) {
            throw ApiException.unauthorized("INVALID_PASSWORD", "La contraseña actual no es correcta");
        }
        passwordPolicy.validate(request.newPassword());
        if (passwordHasher.matches(request.newPassword(), user.passwordHash)) {
            throw ApiException.badRequest("PASSWORD_REUSED", "La nueva contraseña debe ser distinta a la actual");
        }
        user.passwordHash = passwordHasher.hash(request.newPassword());
        RefreshToken.revokeAllForUser(user);
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        String email = User.normalizeEmail(request.email());
        ResetMailJob[] job = {null};

        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.findByEmail(email).orElse(null);
            if (user == null || !user.enabled || !user.isApproved()) {
                return;
            }

            PasswordResetToken.invalidateAllForUser(user);

            String rawToken = tokenService.newRefreshToken();
            PasswordResetToken stored = new PasswordResetToken();
            stored.user = user;
            stored.tokenHash = tokenService.hash(rawToken);
            stored.expiresAt = Instant.now().plus(properties.passwordResetTokenTtl());
            stored.persist();

            job[0] = new ResetMailJob(user.email, rawToken);
        });

        if (job[0] != null) {
            // After commit: the reset link is valid as soon as the email arrives.
            mailService.sendPasswordReset(job[0].email(), job[0].rawToken());
        }
        return FORGOT_PASSWORD_MESSAGE;
    }

    private record ResetMailJob(String email, String rawToken) {
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        String hash = tokenService.hash(request.token());
        PasswordResetToken stored = PasswordResetToken.findActiveByHash(hash)
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "El enlace no es válido o ya fue usado"));

        if (!stored.isActive(now)) {
            stored.used = true;
            throw ApiException.badRequest("INVALID_RESET_TOKEN", "El enlace expiró. Pedí uno nuevo.");
        }

        User user = stored.user;
        if (user.isDeleted() || !user.enabled) {
            stored.used = true;
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está deshabilitada");
        }
        if (!user.isApproved()) {
            stored.used = true;
            throw ApiException.forbidden(
                    "CUENTA_PENDIENTE",
                    "Tu cuenta todavía no fue aprobada. Cuando el profesor o el administrador la habilite, vas a poder entrar."
            );
        }

        passwordPolicy.validate(request.newPassword());
        user.passwordHash = passwordHasher.hash(request.newPassword());
        user.failedLoginAttempts = 0;
        user.lockedUntil = null;
        stored.used = true;
        PasswordResetToken.invalidateAllForUser(user);
        RefreshToken.revokeAllForUser(user);
    }

    private User requireUser(String userId) {
        User user = User.findActiveById(userId).orElse(null);
        if (user == null || !user.enabled) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Sesión inválida");
        }
        return user;
    }

    private AuthResponse issueSession(User user) {
        String refreshToken = tokenService.newRefreshToken();
        RefreshToken stored = new RefreshToken();
        stored.user = user;
        stored.tokenHash = tokenService.hash(refreshToken);
        stored.expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        stored.persist();

        String accessToken = jwtIssuer.issueAccessToken(user);
        return AuthResponse.of(accessToken, refreshToken, jwtIssuer.expiresInSeconds(), UserResponse.from(user));
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Email o contraseña incorrectos");
    }
}
