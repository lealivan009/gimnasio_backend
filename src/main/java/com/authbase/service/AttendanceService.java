package com.authbase.service;

import com.authbase.api.dto.GymDtos.AttendanceResponse;
import com.authbase.domain.AttendanceLog;
import com.authbase.domain.BookingStatus;
import com.authbase.domain.ClassBooking;
import com.authbase.domain.GymConfig;
import com.authbase.domain.User;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
@Transactional
public class AttendanceService {

    private final GymConfigService gymConfigService;

    public AttendanceService(GymConfigService gymConfigService) {
        this.gymConfigService = gymConfigService;
    }

    public List<AttendanceResponse> list(User actor, String studentId, LocalDate from, LocalDate to) {
        String ownerId = owner(actor, studentId);
        List<AttendanceLog> logs = AttendanceLog.list(
                "student.id = ?1 order by checkInTime desc",
                ownerId
        );
        return logs.stream()
                .filter(log -> within(log.checkInTime, from, to))
                .map(this::toResponse)
                .toList();
    }

    public AttendanceResponse today(User actor) {
        if (!actor.role.isStudent()) {
            return null;
        }
        AttendanceLog log = AttendanceLog.find(
                "student.id = ?1 and checkInTime >= ?2 and checkInTime < ?3 order by checkInTime desc",
                actor.id,
                GymTimes.startOfDay(GymTimes.today()),
                GymTimes.endOfDay(GymTimes.today())
        ).firstResult();
        return log == null ? null : toResponse(log);
    }

    public List<AttendanceResponse> insideNow(User actor) {
        if (!actor.role.isStaff()) {
            throw ApiException.forbidden("FORBIDDEN", "Solo el profesor puede ver quién está en el gimnasio");
        }
        List<AttendanceLog> logs = AttendanceLog.list(
                "checkOutTime is null and checkInTime >= ?1 and checkInTime < ?2 order by checkInTime",
                GymTimes.startOfDay(GymTimes.today()),
                GymTimes.endOfDay(GymTimes.today())
        );
        return logs.stream().map(this::toResponse).toList();
    }

    public boolean wasPresent(String studentId, LocalDate date) {
        return AttendanceLog.count(
                "student.id = ?1 and checkInTime >= ?2 and checkInTime < ?3",
                studentId,
                GymTimes.startOfDay(date),
                GymTimes.endOfDay(date)
        ) > 0;
    }

    @Transactional
    public AttendanceResponse checkIn(User actor, String studentId, LocalDate fecha) {
        LocalDate today = GymTimes.today();
        LocalDate date = fecha == null ? today : fecha;
        if (date.isAfter(today)) {
            throw ApiException.badRequest("FECHA_FUTURA", "No podés marcar presente en un día que todavía no llegó");
        }
        if (date.isBefore(today.minusDays(7))) {
            throw ApiException.badRequest(
                    "FECHA_ANTIGUA",
                    "No podés marcar presente con más de una semana de antigüedad"
            );
        }
        if (!date.equals(today)) {
            return checkInPast(actor, studentId, date);
        }
        return checkInToday(actor, studentId);
    }

    private AttendanceResponse checkInToday(User actor, String studentId) {
        User student = resolveStudent(actor, studentId);
        if (openToday(student.id) != null) {
            throw ApiException.conflict(
                    "YA_ADENTRO",
                    actor.role.isStaff()
                            ? "Ese alumno ya está adentro"
                            : "Ya registraste tu ingreso y todavía no marcaste la salida"
            );
        }
        GymConfig config = GymConfig.current();
        if (!gymConfigService.isOpenNow()) {
            throw ApiException.badRequest("GIMNASIO_CERRADO", "El gimnasio está cerrado en este horario");
        }
        if (gymConfigService.enforcesFloorCapacity(config.operatingMode)) {
            long inside = AttendanceLog.count(
                    "checkOutTime is null and checkInTime >= ?1 and checkInTime < ?2",
                    GymTimes.startOfDay(GymTimes.today()),
                    GymTimes.endOfDay(GymTimes.today())
            );
            if (inside >= config.maxCapacityPerSlot) {
                throw ApiException.conflict("SIN_CAPACIDAD", "El gimnasio alcanzó la capacidad máxima");
            }
        }

        AttendanceLog log = new AttendanceLog();
        log.student = student;
        log.checkInTime = Instant.now();
        log.persist();

        List<ClassBooking> bookings = ClassBooking.list(
                "student.id = ?1 and bookingDate = ?2 and status = ?3",
                student.id,
                GymTimes.today(),
                BookingStatus.CONFIRMADA
        );
        for (ClassBooking booking : bookings) {
            booking.status = BookingStatus.ASISTIO;
        }
        return toResponse(log);
    }

    private AttendanceResponse checkInPast(User actor, String studentId, LocalDate date) {
        User student = resolveStudent(actor, studentId);
        if (wasPresent(student.id, date)) {
            throw ApiException.conflict("YA_PRESENTE", "Ese día ya tiene el presente cargado");
        }
        Instant at = date.atTime(12, 0).atZone(GymTimes.ZONE).toInstant();
        AttendanceLog log = new AttendanceLog();
        log.student = student;
        log.checkInTime = at;
        log.checkOutTime = at;
        log.persist();
        return toResponse(log);
    }

    @Transactional
    public AttendanceResponse checkOut(User actor, String studentId) {
        User student = resolveStudent(actor, studentId);
        AttendanceLog open = openToday(student.id);
        if (open == null) {
            throw ApiException.badRequest(
                    "SIN_INGRESO",
                    actor.role.isStaff()
                            ? "Ese alumno no está adentro"
                            : "No tenés un ingreso abierto para marcar la salida"
            );
        }
        open.checkOutTime = Instant.now();
        return toResponse(open);
    }

    private User resolveStudent(User actor, String studentId) {
        if (actor.role.isStudent()) {
            return actor;
        }
        if (!actor.role.isStaff()) {
            throw ApiException.forbidden("FORBIDDEN", "No podés cargar la asistencia");
        }
        return requireStudent(studentId);
    }

    private User requireStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw ApiException.badRequest("ALUMNO_REQUERIDO", "Tenés que indicar el alumno");
        }
        User student = User.findActiveById(studentId)
                .orElseThrow(() -> ApiException.notFound("ALUMNO_NO_ENCONTRADO", "El alumno no existe"));
        if (!student.role.isStudent()) {
            throw ApiException.badRequest("NO_ES_ALUMNO", "Esa cuenta no es de un alumno");
        }
        return student;
    }

    private AttendanceLog openToday(String studentId) {
        return AttendanceLog.find(
                "student.id = ?1 and checkOutTime is null and checkInTime >= ?2 and checkInTime < ?3",
                studentId,
                GymTimes.startOfDay(GymTimes.today()),
                GymTimes.endOfDay(GymTimes.today())
        ).firstResult();
    }

    private boolean within(Instant instant, LocalDate from, LocalDate to) {
        LocalDate day = GymTimes.toLocalDate(instant);
        if (from != null && day.isBefore(from)) {
            return false;
        }
        return to == null || !day.isAfter(to);
    }

    private String owner(User actor, String studentId) {
        if (actor.role.isStudent()) {
            if (studentId != null && !studentId.isBlank() && !studentId.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés ver la asistencia de otra persona");
            }
            return actor.id;
        }
        if (studentId == null || studentId.isBlank()) {
            throw ApiException.badRequest("ALUMNO_REQUERIDO", "Tenés que indicar el alumno");
        }
        User student = User.findActiveById(studentId)
                .orElseThrow(() -> ApiException.notFound("ALUMNO_NO_ENCONTRADO", "El alumno no existe"));
        return student.id;
    }

    private AttendanceResponse toResponse(AttendanceLog log) {
        return new AttendanceResponse(
                log.id,
                log.student.id,
                log.student.displayName(),
                log.checkInTime,
                log.checkOutTime
        );
    }
}
