package com.authbase.api.dto;

import com.authbase.domain.BookingStatus;
import com.authbase.domain.GymTheme;
import com.authbase.domain.MuscleGroup;
import com.authbase.domain.OperatingMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GymDtos {

    private GymDtos() {
    }

    public record OpeningHourResponse(
            int dayOfWeek,
            String opensAt,
            String closesAt
    ) {
    }

    public record OpeningHourInput(
            @Min(value = 1, message = "El día tiene que estar entre 1 (lunes) y 7 (domingo)")
            @Max(value = 7, message = "El día tiene que estar entre 1 (lunes) y 7 (domingo)")
            int dayOfWeek,
            @NotBlank(message = "La hora de apertura es obligatoria")
            String opensAt,
            @NotBlank(message = "La hora de cierre es obligatoria")
            String closesAt
    ) {
    }

    public record GymConfigResponse(
            OperatingMode operatingMode,
            String openingTime,
            String closingTime,
            int maxCapacityPerSlot,
            String name,
            String address,
            String mapUrl,
            GymTheme theme,
            List<OpeningHourResponse> hours,
            String description
    ) {
    }

    public record PublicGymResponse(
            String name,
            String openingTime,
            String closingTime,
            String address,
            String mapUrl,
            GymTheme theme,
            List<OpeningHourResponse> hours,
            String description
    ) {
    }

    public record UpdateGymConfigRequest(
            @NotNull(message = "La modalidad es obligatoria")
            OperatingMode operatingMode,
            @NotBlank(message = "La hora de apertura es obligatoria")
            String openingTime,
            @NotBlank(message = "La hora de cierre es obligatoria")
            String closingTime,
            @Min(value = 1, message = "La capacidad tiene que ser al menos 1")
            int maxCapacityPerSlot,
            @Size(max = 120, message = "El nombre del gimnasio es demasiado largo")
            String name,
            @Size(max = 200, message = "La dirección es demasiado larga")
            String address,
            @Size(max = 500, message = "El enlace del mapa es demasiado largo")
            String mapUrl,
            GymTheme theme,
            @Valid
            List<OpeningHourInput> hours,
            @Size(max = 1000, message = "La descripción es demasiado larga")
            String description
    ) {
    }

    public record OccupancyResponse(
            OperatingMode operatingMode,
            int currentOccupancy,
            int maxCapacity,
            boolean openNow
    ) {
    }

    public record ExerciseResponse(
            String id,
            String name,
            String description,
            MuscleGroup muscleGroup,
            String videoUrl,
            Instant createdAt
    ) {
    }

    public record UpsertExerciseRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 120, message = "El nombre es demasiado largo")
            String name,
            @Size(max = 2000, message = "La descripción es demasiado larga")
            String description,
            @NotNull(message = "El grupo muscular es obligatorio")
            MuscleGroup muscleGroup,
            @Size(max = 500, message = "El enlace del video es demasiado largo")
            String videoUrl
    ) {
    }

    public record InstructorResponse(String id, String firstName, String lastName) {
    }

    public record ClassScheduleResponse(
            String id,
            String title,
            String instructorId,
            String instructorName,
            int dayOfWeek,
            String startTime,
            String endTime,
            int capacity,
            int bookedCount,
            int remaining,
            boolean active
    ) {
    }

    public record UpsertClassScheduleRequest(
            @NotBlank(message = "El título es obligatorio")
            @Size(max = 120, message = "El título es demasiado largo")
            String title,
            @NotBlank(message = "El profesor es obligatorio")
            String instructorId,
            @Min(value = 1, message = "El día tiene que estar entre 1 (lunes) y 7 (domingo)")
            @Max(value = 7, message = "El día tiene que estar entre 1 (lunes) y 7 (domingo)")
            int dayOfWeek,
            @NotBlank(message = "La hora de inicio es obligatoria")
            String startTime,
            @NotBlank(message = "La hora de fin es obligatoria")
            String endTime,
            @Min(value = 1, message = "El cupo tiene que ser al menos 1")
            int capacity,
            Boolean active
    ) {
    }

    public record BookClassRequest(
            @NotNull(message = "La fecha es obligatoria")
            LocalDate date
    ) {
    }

    public record BookingResponse(
            String id,
            String classScheduleId,
            String classTitle,
            String studentId,
            String studentName,
            LocalDate date,
            BookingStatus status,
            String startTime,
            String endTime
    ) {
    }

    public record UpdateBookingStatusRequest(
            @NotNull(message = "El estado es obligatorio")
            BookingStatus status
    ) {
    }

    public record RoutineExerciseInput(
            @NotBlank(message = "El ejercicio es obligatorio")
            String exerciseId,
            @Min(value = 0, message = "El orden no puede ser negativo")
            int orderIndex,
            @Min(value = 1, message = "Las series tienen que ser al menos 1")
            @Max(value = 30, message = "Las series no pueden superar 30")
            int targetSets,
            @Min(value = 1, message = "Las repeticiones tienen que ser al menos 1")
            @Max(value = 999, message = "Las repeticiones no pueden superar 999")
            int targetReps,
            @Min(value = 0, message = "El descanso no puede ser negativo")
            @Max(value = 900, message = "El descanso no puede superar 900 segundos")
            int targetRestSeconds,
            @Size(max = 500, message = "Las notas son demasiado largas")
            String notes,
            @Min(value = 1, message = "El día tiene que ser entre 1 y 7")
            @Max(value = 7, message = "El día tiene que ser entre 1 y 7")
            Integer dayNumber,
            @Size(max = 80, message = "El nombre del día es demasiado largo")
            String dayLabel
    ) {
    }

    public record RoutineExerciseResponse(
            String id,
            String exerciseId,
            String exerciseName,
            MuscleGroup muscleGroup,
            int orderIndex,
            int targetSets,
            int targetReps,
            int targetRestSeconds,
            String notes,
            int dayNumber,
            String dayLabel
    ) {
    }

    public record RoutineResponse(
            String id,
            String title,
            String description,
            String createdByUserId,
            String createdByName,
            boolean customStudent,
            List<RoutineExerciseResponse> exercises
    ) {
    }

    public record UpsertRoutineRequest(
            @NotBlank(message = "El título es obligatorio")
            @Size(max = 120, message = "El título es demasiado largo")
            String title,
            @Size(max = 2000, message = "La descripción es demasiado larga")
            String description,
            @NotEmpty(message = "La rutina tiene que incluir al menos un ejercicio")
            List<@Valid RoutineExerciseInput> exercises,
            LocalDate startDate,
            LocalDate expirationDate
    ) {
    }

    public record CloneRoutineRequest(
            @Size(max = 120, message = "El título es demasiado largo")
            String title
    ) {
    }

    public record AssignRoutineRequest(
            @NotBlank(message = "El alumno es obligatorio")
            String studentId,
            @NotNull(message = "La fecha de inicio es obligatoria")
            LocalDate startDate,
            LocalDate expirationDate
    ) {
    }

    public record UpdateAssignmentRequest(
            @NotNull(message = "La fecha de inicio es obligatoria")
            LocalDate startDate,
            LocalDate expirationDate,
            Boolean active
    ) {
    }

    public record AssignmentResponse(
            String id,
            String studentId,
            String studentName,
            String routineId,
            String routineTitle,
            boolean customStudent,
            LocalDate startDate,
            LocalDate expirationDate,
            boolean active,
            boolean expired
    ) {
    }

    public record ActiveRoutineResponse(
            AssignmentResponse assignment,
            RoutineResponse routine,
            AssignmentResponse expired
    ) {
    }

    public record CreateWorkoutLogRequest(
            @NotBlank(message = "El ejercicio es obligatorio")
            String exerciseId,
            String routineId,
            @Min(value = 1, message = "El número de serie tiene que ser al menos 1")
            @Max(value = 99, message = "El número de serie no puede superar 99")
            int setNumber,
            @Min(value = 0, message = "Las repeticiones no pueden ser negativas")
            @Max(value = 999, message = "Las repeticiones no pueden superar 999")
            int repsCompleted,
            @NotNull(message = "El peso es obligatorio")
            @DecimalMin(value = "0.0", message = "El peso no puede ser negativo")
            @DecimalMax(value = "999.99", message = "El peso es demasiado alto")
            BigDecimal weightKg,
            @Min(value = 1, message = "El esfuerzo percibido tiene que estar entre 1 y 10")
            @Max(value = 10, message = "El esfuerzo percibido tiene que estar entre 1 y 10")
            Integer rpe,
            @Min(value = 1, message = "El día tiene que ser entre 1 y 7")
            @Max(value = 7, message = "El día tiene que ser entre 1 y 7")
            Integer dayNumber,
            @Size(max = 80, message = "El nombre del día es demasiado largo")
            String dayLabel,
            LocalDate loggedDate
    ) {
    }

    public record WorkoutLogResponse(
            String id,
            String studentId,
            String exerciseId,
            String exerciseName,
            Instant loggedAt,
            int setNumber,
            int repsCompleted,
            BigDecimal weightKg,
            Integer rpe,
            String routineId,
            LocalDate loggedDate,
            Integer dayNumber,
            String dayLabel
    ) {
    }

    public record AttendanceResponse(
            String id,
            String studentId,
            String studentName,
            Instant checkInTime,
            Instant checkOutTime
    ) {
    }

    public record ExerciseProgressPoint(
            LocalDate date,
            BigDecimal maxWeightKg,
            BigDecimal volumeKg,
            int sets
    ) {
    }

    public record PersonalRecordResponse(
            String exerciseId,
            String exerciseName,
            BigDecimal maxWeightKg,
            Instant achievedAt
    ) {
    }

    public record StatsSummaryResponse(
            int weeklyStreak,
            int workoutsThisWeek,
            int attendanceDaysThisMonth,
            List<PersonalRecordResponse> records
    ) {
    }

    public record CalendarSessionResponse(
            int dayNumber,
            String label
    ) {
    }

    public record CalendarDayResponse(
            LocalDate date,
            boolean attended,
            boolean trained,
            int sets,
            List<CalendarSessionResponse> sessions
    ) {
    }

    public record WeightLogResponse(
            String id,
            String studentId,
            BigDecimal weightKg,
            LocalDate recordedOn
    ) {
    }

    public record CreateWeightLogRequest(
            @NotNull(message = "El peso es obligatorio")
            @DecimalMin(value = "20.0", message = "El peso tiene que ser de al menos 20 kg")
            @DecimalMax(value = "400.0", message = "El peso no puede superar 400 kg")
            BigDecimal weightKg,
            LocalDate recordedOn
    ) {
    }

    public record StudentFollowResponse(
            String id,
            String firstName,
            String lastName,
            String email,
            String activeRoutineTitle,
            LocalDate routineStartDate,
            LocalDate routineExpiration,
            boolean routineCustom,
            Instant lastCheckIn,
            long attendanceLast30Days
    ) {
    }
}
