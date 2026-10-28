package com.authbase.service;

import com.authbase.api.dto.CreateUserRequest;
import com.authbase.api.dto.UpdateUserRequest;
import com.authbase.api.dto.UserPageResponse;
import com.authbase.api.dto.UserResponse;
import com.authbase.domain.RefreshToken;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.helpers.UserProfiles;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import com.authbase.security.PasswordHasher;
import com.authbase.security.PasswordPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class UserService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final WeightService weightService;
    private final AttendanceService attendanceService;

    public UserService(
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            WeightService weightService,
            AttendanceService attendanceService
    ) {
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.weightService = weightService;
        this.attendanceService = attendanceService;
    }

    public UserPageResponse list(String q, int page, int size, Boolean enabled, Role role) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        StringBuilder where = new StringBuilder("deletedAt is null");
        Map<String, Object> params = new HashMap<>();

        if (q != null && !q.isBlank()) {
            where.append("""
                     and (
                        lower(email) like :q
                        or lower(firstName) like :q
                        or lower(lastName) like :q
                        or lower(coalesce(phone, '')) like :q
                     )
                    """);
            params.put("q", "%" + q.trim().toLowerCase() + "%");
        }
        if (enabled != null) {
            where.append(" and enabled = :enabled");
            params.put("enabled", enabled);
        }
        if (role != null) {
            where.append(" and role = :role");
            params.put("role", role);
        }

        String filter = where.toString();
        long total = User.count(filter, params);
        List<UserResponse> items = User.<User>find(filter + " ORDER BY lower(lastName) ASC, lower(firstName) ASC", params)
                .page(safePage, safeSize)
                .list()
                .stream()
                .map(UserResponse::from)
                .toList();

        return UserPageResponse.of(items, total, safePage, safeSize);
    }

    public UserResponse get(String id) {
        return UserResponse.from(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
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
        user.role = request.role() == null ? Role.ALUMNO : request.role();
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
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(String id, String actorId, UpdateUserRequest request) {
        User user = requireUser(id);
        boolean self = user.id.equals(actorId);

        boolean editingProfile = request.firstName() != null || request.lastName() != null;
        if (editingProfile) {
            if (request.firstName() != null) {
                user.firstName = request.firstName().trim();
            }
            if (request.lastName() != null) {
                user.lastName = request.lastName().trim();
            }
            // Full profile save: blank values clear the field (admin ficha / detalle).
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
        }
        if (request.role() != null && request.role() != user.role) {
            if (self) {
                throw ApiException.badRequest("CANNOT_CHANGE_OWN_ROLE", "No podés cambiar tu propio rol");
            }
            if (user.role == Role.ADMIN && request.role() != Role.ADMIN && adminCount() <= 1) {
                throw ApiException.badRequest("LAST_ADMIN", "Tiene que quedar al menos un administrador");
            }
            user.role = request.role();
            RefreshToken.revokeAllForUser(user);
        }
        if (request.enabled() != null && request.enabled() != user.enabled) {
            if (self) {
                throw ApiException.badRequest("CANNOT_DISABLE_SELF", "No podés deshabilitar tu propia cuenta");
            }
            user.enabled = request.enabled();
            if (user.enabled) {
                user.failedLoginAttempts = 0;
                user.lockedUntil = null;
            } else {
                RefreshToken.revokeAllForUser(user);
            }
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(String id, String actorId) {
        User user = requireUser(id);
        if (user.id.equals(actorId)) {
            throw ApiException.badRequest("CANNOT_DELETE_SELF", "No podés borrar tu propia cuenta");
        }
        if (user.role == Role.ADMIN && adminCount() <= 1) {
            throw ApiException.badRequest("LAST_ADMIN", "Tiene que quedar al menos un administrador");
        }
        // Soft delete: keep the row for FK integrity with future related tables.
        // Free the unique email so the address can be registered again.
        String originalEmail = user.email;
        String tagged = "deleted." + user.id + "." + originalEmail;
        user.deletedAt = Instant.now();
        user.enabled = false;
        user.email = tagged.length() <= 255 ? tagged : ("deleted." + user.id);
        RefreshToken.revokeAllForUser(user);
    }

    private User requireUser(String id) {
        return User.findActiveById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No se encontró el usuario"));
    }

    private long adminCount() {
        return User.count("role = ?1 and deletedAt is null", Role.ADMIN);
    }
}
