package com.authbase.service;

import com.authbase.api.dto.GymDtos.BookClassRequest;
import com.authbase.api.dto.GymDtos.BookingResponse;
import com.authbase.api.dto.GymDtos.ClassScheduleResponse;
import com.authbase.api.dto.GymDtos.InstructorResponse;
import com.authbase.api.dto.GymDtos.UpdateBookingStatusRequest;
import com.authbase.api.dto.GymDtos.UpsertClassScheduleRequest;
import com.authbase.domain.BookingStatus;
import com.authbase.domain.ClassBooking;
import com.authbase.domain.ClassSchedule;
import com.authbase.domain.GymConfig;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@ApplicationScoped
@Transactional
public class ClassService {

    private final GymConfigService gymConfigService;

    public ClassService(GymConfigService gymConfigService) {
        this.gymConfigService = gymConfigService;
    }

    public List<InstructorResponse> instructors() {
        return User.<User>list(
                        "deletedAt is null and enabled = true and role in ?1 order by lastName, firstName",
                        List.of(Role.ADMIN, Role.PROFESOR)
                )
                .stream()
                .map(user -> new InstructorResponse(user.id, user.firstName, user.lastName))
                .toList();
    }

    public List<ClassScheduleResponse> list(LocalDate date, Boolean activeOnly) {
        String query = activeOnly != null && activeOnly
                ? "active = true order by dayOfWeek, startTime"
                : "order by dayOfWeek, startTime";
        return ClassSchedule.<ClassSchedule>list(query)
                .stream()
                .filter(schedule -> date == null || schedule.dayOfWeek == date.getDayOfWeek().getValue())
                .map(schedule -> toResponse(schedule, date))
                .toList();
    }

    @Transactional
    public ClassScheduleResponse create(UpsertClassScheduleRequest request) {
        ClassSchedule schedule = new ClassSchedule();
        apply(schedule, request);
        schedule.persist();
        return toResponse(schedule, null);
    }

    @Transactional
    public ClassScheduleResponse update(String id, UpsertClassScheduleRequest request) {
        ClassSchedule schedule = requireSchedule(id);
        apply(schedule, request);
        return toResponse(schedule, null);
    }

    @Transactional
    public void deactivate(String id) {
        ClassSchedule schedule = requireSchedule(id);
        schedule.active = false;
    }

    public List<BookingResponse> listBookings(User actor, LocalDate date, String studentId) {
        String ownerId = resolveStudentFilter(actor, studentId);
        StringBuilder query = new StringBuilder("1 = 1");
        if (ownerId != null) {
            query.append(" and student.id = ?1");
        }
        if (date != null) {
            query.append(ownerId != null ? " and bookingDate = ?2" : " and bookingDate = ?1");
        }
        query.append(" order by bookingDate desc, createdAt desc");

        List<ClassBooking> bookings;
        if (ownerId != null && date != null) {
            bookings = ClassBooking.list(query.toString(), ownerId, date);
        } else if (ownerId != null) {
            bookings = ClassBooking.list(query.toString(), ownerId);
        } else if (date != null) {
            bookings = ClassBooking.list(query.toString(), date);
        } else {
            bookings = ClassBooking.list("order by bookingDate desc, createdAt desc");
        }
        return bookings.stream().map(this::toBooking).toList();
    }

    @Transactional
    public BookingResponse book(User actor, String scheduleId, BookClassRequest request) {
        if (!actor.role.isStudent()) {
            throw ApiException.forbidden("FORBIDDEN", "Solo un alumno puede inscribirse a un turno");
        }
        GymConfig config = GymConfig.current();
        if (!gymConfigService.acceptsBookings(config.operatingMode)) {
            throw ApiException.badRequest(
                    "MODALIDAD_INVALIDA",
                    "El gimnasio está en acceso libre y no admite reservas de turnos"
            );
        }
        ClassSchedule schedule = requireSchedule(scheduleId);
        if (!schedule.active) {
            throw ApiException.badRequest("TURNO_INACTIVO", "Este turno no está vigente");
        }
        LocalDate date = request.date();
        LocalDate today = GymTimes.today();
        if (date.isBefore(today)) {
            throw ApiException.badRequest("FECHA_PASADA", "No se puede reservar un turno de un día anterior");
        }
        if (date.getDayOfWeek().getValue() != schedule.dayOfWeek) {
            throw ApiException.badRequest("DIA_INVALIDO", "La fecha no corresponde al día de este turno");
        }
        ClassBooking existing = ClassBooking.find(
                "classSchedule.id = ?1 and student.id = ?2 and bookingDate = ?3 and status <> ?4",
                schedule.id,
                actor.id,
                date,
                BookingStatus.CANCELADA
        ).firstResult();
        if (existing != null) {
            throw ApiException.conflict("YA_RESERVADO", "Ya tenés una reserva para este turno");
        }
        long taken = takenSeats(schedule.id, date);
        if (taken >= schedule.capacity) {
            throw ApiException.conflict("SIN_CUPO", "El turno seleccionado no tiene cupos disponibles");
        }

        ClassBooking booking = new ClassBooking();
        booking.classSchedule = schedule;
        booking.student = actor;
        booking.bookingDate = date;
        booking.status = BookingStatus.CONFIRMADA;
        booking.persist();
        return toBooking(booking);
    }

    @Transactional
    public BookingResponse cancel(User actor, String bookingId) {
        ClassBooking booking = requireBooking(bookingId);
        if (actor.role.isStudent() && !booking.student.id.equals(actor.id)) {
            throw ApiException.forbidden("FORBIDDEN", "No podés cancelar la reserva de otra persona");
        }
        if (booking.status == BookingStatus.ASISTIO) {
            throw ApiException.badRequest("NO_CANCELABLE", "No se puede cancelar un turno al que ya asististe");
        }
        if (booking.status == BookingStatus.CANCELADA) {
            throw ApiException.badRequest("YA_CANCELADA", "La reserva ya estaba cancelada");
        }
        booking.status = BookingStatus.CANCELADA;
        return toBooking(booking);
    }

    @Transactional
    public BookingResponse updateStatus(String bookingId, UpdateBookingStatusRequest request) {
        ClassBooking booking = requireBooking(bookingId);
        booking.status = request.status();
        return toBooking(booking);
    }

    public long takenSeats(String scheduleId, LocalDate date) {
        return ClassBooking.count(
                "classSchedule.id = ?1 and bookingDate = ?2 and status <> ?3",
                scheduleId,
                date,
                BookingStatus.CANCELADA
        );
    }

    private void apply(ClassSchedule schedule, UpsertClassScheduleRequest request) {
        User instructor = User.findActiveById(request.instructorId())
                .orElseThrow(() -> ApiException.badRequest("PROFESOR_INVALIDO", "El profesor seleccionado no es válido"));
        if (!instructor.role.isStaff()) {
            throw ApiException.badRequest("PROFESOR_INVALIDO", "El profesor seleccionado no es válido");
        }
        LocalTime start = GymTimes.parse(request.startTime(), "hora de inicio");
        LocalTime end = GymTimes.parse(request.endTime(), "hora de fin");
        GymTimes.requireRange(start, end, "La hora de fin debe ser posterior a la de inicio");

        schedule.title = request.title().trim();
        schedule.instructor = instructor;
        schedule.dayOfWeek = request.dayOfWeek();
        schedule.startTime = start;
        schedule.endTime = end;
        schedule.capacity = request.capacity();
        schedule.active = request.active() == null || request.active();
    }

    private String resolveStudentFilter(User actor, String studentId) {
        if (actor.role.isStudent()) {
            if (studentId != null && !studentId.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés ver las reservas de otra persona");
            }
            return actor.id;
        }
        return studentId == null || studentId.isBlank() ? null : studentId;
    }

    private ClassSchedule requireSchedule(String id) {
        ClassSchedule schedule = ClassSchedule.findById(id);
        if (schedule == null) {
            throw ApiException.notFound("TURNO_NO_ENCONTRADO", "El turno no existe");
        }
        return schedule;
    }

    private ClassBooking requireBooking(String id) {
        ClassBooking booking = ClassBooking.findById(id);
        if (booking == null) {
            throw ApiException.notFound("RESERVA_NO_ENCONTRADA", "La reserva no existe");
        }
        return booking;
    }

    private ClassScheduleResponse toResponse(ClassSchedule schedule, LocalDate date) {
        int booked = date == null ? 0 : (int) takenSeats(schedule.id, date);
        int remaining = date == null ? schedule.capacity : Math.max(0, schedule.capacity - booked);
        return new ClassScheduleResponse(
                schedule.id,
                schedule.title,
                schedule.instructor.id,
                schedule.instructor.displayName(),
                schedule.dayOfWeek,
                GymTimes.format(schedule.startTime),
                GymTimes.format(schedule.endTime),
                schedule.capacity,
                booked,
                remaining,
                schedule.active
        );
    }

    private BookingResponse toBooking(ClassBooking booking) {
        return new BookingResponse(
                booking.id,
                booking.classSchedule.id,
                booking.classSchedule.title,
                booking.student.id,
                booking.student.displayName(),
                booking.bookingDate,
                booking.status,
                GymTimes.format(booking.classSchedule.startTime),
                GymTimes.format(booking.classSchedule.endTime)
        );
    }
}
