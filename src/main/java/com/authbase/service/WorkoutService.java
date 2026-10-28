package com.authbase.service;

import com.authbase.api.dto.GymDtos.CreateWorkoutLogRequest;
import com.authbase.api.dto.GymDtos.WorkoutLogResponse;
import com.authbase.domain.Exercise;
import com.authbase.domain.Routine;
import com.authbase.domain.RoutineExercise;
import com.authbase.domain.User;
import com.authbase.domain.WorkoutLog;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
public class WorkoutService {

    private final ExerciseService exerciseService;
    private final RoutineService routineService;

    public WorkoutService(ExerciseService exerciseService, RoutineService routineService) {
        this.exerciseService = exerciseService;
        this.routineService = routineService;
    }

    public List<WorkoutLogResponse> list(User actor, String studentId, String exerciseId, LocalDate from, LocalDate to) {
        String ownerId = owner(actor, studentId);
        StringBuilder query = new StringBuilder("student.id = ?1");
        if (exerciseId != null && !exerciseId.isBlank()) {
            query.append(" and exercise.id = ?2");
        }
        query.append(" order by loggedAt desc");

        List<WorkoutLog> logs = exerciseId != null && !exerciseId.isBlank()
                ? WorkoutLog.list(query.toString(), ownerId, exerciseId)
                : WorkoutLog.list(query.toString(), ownerId);

        return logs.stream()
                .filter(log -> within(log, from, to))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorkoutLogResponse create(User actor, CreateWorkoutLogRequest request) {
        if (!actor.role.isStudent() && !actor.role.isStaff()) {
            throw ApiException.forbidden("FORBIDDEN", "No tenés permiso para registrar entrenamientos");
        }
        LocalDate date = resolveLogDate(request.loggedDate());
        routineService.ensureLoggable(actor, request.routineId(), date);
        Exercise exercise = exerciseService.require(request.exerciseId());
        Routine routine = null;
        if (request.routineId() != null && !request.routineId().isBlank()) {
            routine = Routine.findById(request.routineId());
            if (routine == null) {
                throw ApiException.notFound("RUTINA_NO_ENCONTRADA", "La rutina no existe");
            }
        }

        WorkoutLog log = new WorkoutLog();
        log.student = actor;
        log.exercise = exercise;
        log.routine = routine;
        log.setNumber = request.setNumber();
        log.repsCompleted = request.repsCompleted();
        log.weightKg = request.weightKg();
        log.rpe = request.rpe();
        log.loggedAt = date.equals(GymTimes.today())
                ? Instant.now()
                : date.atTime(12, 0).atZone(GymTimes.ZONE).toInstant();
        applyDay(log, routine, request.dayNumber(), request.dayLabel());
        log.persist();
        return toResponse(log);
    }

    private LocalDate resolveLogDate(LocalDate requested) {
        LocalDate today = GymTimes.today();
        LocalDate date = requested == null ? today : requested;
        if (date.isAfter(today)) {
            throw ApiException.badRequest("FECHA_FUTURA", "No puedes registrar un entrenamiento en una fecha futura");
        }
        if (date.isBefore(today.minusDays(7))) {
            throw ApiException.badRequest(
                    "FECHA_ANTIGUA",
                    "No puedes registrar entrenamientos con más de una semana de antigüedad"
            );
        }
        return date;
    }

    private void applyDay(WorkoutLog log, Routine routine, Integer requested, String requestedLabel) {
        if (routine == null) {
            log.dayNumber = requested;
            log.dayLabel = ExerciseService.blankToNull(requestedLabel);
            return;
        }
        List<RoutineExercise> exercises = RoutineExercise.list("routine.id = ?1 order by orderIndex", routine.id);
        Map<Integer, String> days = new LinkedHashMap<>();
        for (RoutineExercise item : exercises) {
            int number = item.dayNumber == null || item.dayNumber < 1 ? 1 : item.dayNumber;
            days.putIfAbsent(number, ExerciseService.blankToNull(item.dayLabel));
        }
        if (days.isEmpty()) {
            log.dayNumber = requested == null ? 1 : requested;
            log.dayLabel = ExerciseService.blankToNull(requestedLabel);
            return;
        }
        if (requested == null) {
            if (days.size() > 1) {
                throw ApiException.badRequest("DIA_OBLIGATORIO", "Elegí qué día de la rutina estás haciendo");
            }
            requested = days.keySet().iterator().next();
        }
        if (!days.containsKey(requested)) {
            throw ApiException.badRequest("DIA_INVALIDO", "Ese día no está en la rutina");
        }
        String label = ExerciseService.blankToNull(requestedLabel);
        log.dayNumber = requested;
        log.dayLabel = label != null ? label : days.get(requested);
    }

    @Transactional
    public void delete(User actor, String id) {
        WorkoutLog log = WorkoutLog.findById(id);
        if (log == null) {
            throw ApiException.notFound("REGISTRO_NO_ENCONTRADO", "El registro no existe");
        }
        if (!log.student.id.equals(actor.id) && !actor.role.isStaff()) {
            throw ApiException.forbidden("FORBIDDEN", "No podés borrar el registro de otra persona");
        }
        log.delete();
    }

    private boolean within(WorkoutLog log, LocalDate from, LocalDate to) {
        LocalDate day = GymTimes.toLocalDate(log.loggedAt);
        if (from != null && day.isBefore(from)) {
            return false;
        }
        return to == null || !day.isAfter(to);
    }

    private String owner(User actor, String studentId) {
        if (actor.role.isStudent()) {
            if (studentId != null && !studentId.isBlank() && !studentId.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés ver los registros de otra persona");
            }
            return actor.id;
        }
        if (studentId == null || studentId.isBlank()) {
            return actor.id;
        }
        User student = User.findActiveById(studentId)
                .orElseThrow(() -> ApiException.notFound("ALUMNO_NO_ENCONTRADO", "El alumno no existe"));
        return student.id;
    }

    private WorkoutLogResponse toResponse(WorkoutLog log) {
        return new WorkoutLogResponse(
                log.id,
                log.student.id,
                log.exercise.id,
                log.exercise.name,
                log.loggedAt,
                log.setNumber,
                log.repsCompleted,
                log.weightKg,
                log.rpe,
                log.routine == null ? null : log.routine.id,
                GymTimes.toLocalDate(log.loggedAt),
                log.dayNumber,
                log.dayLabel
        );
    }
}
