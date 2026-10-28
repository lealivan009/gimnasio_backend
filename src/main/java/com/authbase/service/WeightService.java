package com.authbase.service;

import com.authbase.api.dto.GymDtos.CreateWeightLogRequest;
import com.authbase.api.dto.GymDtos.WeightLogResponse;
import com.authbase.domain.User;
import com.authbase.domain.WeightLog;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class WeightService {

    private final AttendanceService attendanceService;

    public WeightService(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public List<WeightLogResponse> list(User actor, String studentId) {
        String ownerId = owner(actor, studentId);
        List<WeightLog> logs = WeightLog.list("student.id = ?1 order by recordedOn, createdAt", ownerId);
        return logs.stream().map(this::toResponse).toList();
    }

    @Transactional
    public WeightLogResponse create(User actor, CreateWeightLogRequest request) {
        if (!actor.role.isStudent()) {
            throw ApiException.forbidden("FORBIDDEN", "Solo el alumno puede cargar su peso");
        }
        return record(actor, request.weightKg(), request.recordedOn());
    }

    @Transactional
    public WeightLogResponse record(User student, BigDecimal weightKg, LocalDate recordedOn) {
        if (weightKg == null) {
            return null;
        }
        LocalDate date = recordedOn == null ? GymTimes.today() : recordedOn;
        if (date.isAfter(GymTimes.today())) {
            throw ApiException.badRequest("FECHA_FUTURA", "La fecha del peso no puede ser futura");
        }
        if (!attendanceService.wasPresent(student.id, date)) {
            throw ApiException.badRequest(
                    "SIN_PRESENTE",
                    "Solo podés cargar el peso los días que tuviste presente"
            );
        }
        WeightLog sameDay = WeightLog.find(
                "student.id = ?1 and recordedOn = ?2 and weightKg = ?3",
                student.id,
                date,
                weightKg
        ).firstResult();
        student.weightKg = weightKg;
        if (sameDay != null) {
            return toResponse(sameDay);
        }
        WeightLog log = new WeightLog();
        log.student = student;
        log.weightKg = weightKg;
        log.recordedOn = date;
        log.persist();
        return toResponse(log);
    }

    private String owner(User actor, String studentId) {
        if (actor.role.isStudent()) {
            if (studentId != null && !studentId.isBlank() && !studentId.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés ver el peso de otra persona");
            }
            return actor.id;
        }
        if (studentId == null || studentId.isBlank()) {
            throw ApiException.badRequest("ALUMNO_REQUERIDO", "Tenés que indicar el alumno");
        }
        User student = User.findActiveById(studentId)
                .orElseThrow(() -> ApiException.notFound("ALUMNO_NO_ENCONTRADO", "El alumno no existe"));
        if (!student.role.isStudent()) {
            throw ApiException.badRequest("NO_ES_ALUMNO", "Esa cuenta no es de un alumno");
        }
        return student.id;
    }

    private WeightLogResponse toResponse(WeightLog log) {
        return new WeightLogResponse(log.id, log.student.id, log.weightKg, log.recordedOn);
    }
}
