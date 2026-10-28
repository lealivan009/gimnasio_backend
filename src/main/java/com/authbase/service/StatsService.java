package com.authbase.service;

import com.authbase.api.dto.GymDtos.CalendarDayResponse;
import com.authbase.api.dto.GymDtos.CalendarSessionResponse;
import com.authbase.api.dto.GymDtos.ExerciseProgressPoint;
import com.authbase.api.dto.GymDtos.PersonalRecordResponse;
import com.authbase.api.dto.GymDtos.StatsSummaryResponse;
import com.authbase.api.dto.GymDtos.StudentFollowResponse;
import com.authbase.domain.AttendanceLog;
import com.authbase.domain.Role;
import com.authbase.domain.StudentRoutineAssignment;
import com.authbase.domain.User;
import com.authbase.domain.WorkoutLog;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
@Transactional
public class StatsService {

    private final ExerciseService exerciseService;

    public StatsService(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    public StatsSummaryResponse summary(User actor, String studentId) {
        String ownerId = owner(actor, studentId);
        List<WorkoutLog> logs = WorkoutLog.list("student.id = ?1", ownerId);
        LocalDate today = GymTimes.today();
        WeekFields weeks = WeekFields.ISO;
        int thisWeek = today.get(weeks.weekOfWeekBasedYear());
        int thisWeekYear = today.get(weeks.weekBasedYear());

        Map<String, Boolean> trainedWeeks = new HashMap<>();
        int workoutsThisWeek = 0;
        Map<String, PersonalRecordResponse> records = new HashMap<>();
        for (WorkoutLog log : logs) {
            LocalDate day = GymTimes.toLocalDate(log.loggedAt);
            String weekKey = day.get(weeks.weekBasedYear()) + "-" + day.get(weeks.weekOfWeekBasedYear());
            trainedWeeks.put(weekKey, true);
            if (day.get(weeks.weekBasedYear()) == thisWeekYear && day.get(weeks.weekOfWeekBasedYear()) == thisWeek) {
                workoutsThisWeek++;
            }
            PersonalRecordResponse current = records.get(log.exercise.id);
            if (current == null || log.weightKg.compareTo(current.maxWeightKg()) > 0
                    || (log.weightKg.compareTo(current.maxWeightKg()) == 0 && log.loggedAt.isBefore(current.achievedAt()))) {
                records.put(log.exercise.id, new PersonalRecordResponse(
                        log.exercise.id,
                        log.exercise.name,
                        log.weightKg,
                        log.loggedAt
                ));
            }
        }

        int streak = 0;
        LocalDate cursor = today;
        while (trainedWeeks.containsKey(cursor.get(weeks.weekBasedYear()) + "-" + cursor.get(weeks.weekOfWeekBasedYear()))) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        List<AttendanceLog> attendance = AttendanceLog.list("student.id = ?1", ownerId);
        long attendanceDays = attendance.stream()
                .map(log -> GymTimes.toLocalDate(log.checkInTime))
                .filter(day -> !day.isBefore(monthStart) && !day.isAfter(today))
                .distinct()
                .count();

        List<PersonalRecordResponse> ordered = records.values().stream()
                .sorted(Comparator.comparing(PersonalRecordResponse::maxWeightKg).reversed())
                .toList();
        return new StatsSummaryResponse(streak, workoutsThisWeek, (int) attendanceDays, ordered);
    }

    public List<ExerciseProgressPoint> exerciseProgress(User actor, String exerciseId, String studentId) {
        exerciseService.require(exerciseId);
        String ownerId = owner(actor, studentId);
        List<WorkoutLog> logs = WorkoutLog.list(
                "student.id = ?1 and exercise.id = ?2 order by loggedAt",
                ownerId,
                exerciseId
        );
        Map<LocalDate, DayBucket> buckets = new HashMap<>();
        for (WorkoutLog log : logs) {
            LocalDate day = GymTimes.toLocalDate(log.loggedAt);
            DayBucket bucket = buckets.computeIfAbsent(day, key -> new DayBucket());
            bucket.sets++;
            bucket.volume = bucket.volume.add(log.weightKg.multiply(BigDecimal.valueOf(log.repsCompleted)));
            if (log.weightKg.compareTo(bucket.maxWeight) > 0) {
                bucket.maxWeight = log.weightKg;
            }
        }
        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ExerciseProgressPoint(
                        entry.getKey(),
                        entry.getValue().maxWeight,
                        entry.getValue().volume,
                        entry.getValue().sets
                ))
                .toList();
    }

    public List<CalendarDayResponse> calendar(User actor, String studentId, Integer year, Integer month) {
        String ownerId = owner(actor, studentId);
        LocalDate start = GymTimes.parseMonth(year, month);
        LocalDate end = start.plusMonths(1).minusDays(1);
        Map<LocalDate, Integer> sets = new HashMap<>();
        Map<LocalDate, Map<Integer, String>> sessionsByDay = new HashMap<>();
        for (WorkoutLog log : WorkoutLog.<WorkoutLog>list("student.id = ?1", ownerId)) {
            LocalDate day = GymTimes.toLocalDate(log.loggedAt);
            if (day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            sets.merge(day, 1, Integer::sum);
            int number = log.dayNumber == null || log.dayNumber < 1 ? 0 : log.dayNumber;
            String label = log.dayLabel != null && !log.dayLabel.isBlank()
                    ? log.dayLabel
                    : (number > 0 ? "Día " + number : null);
            if (label != null) {
                sessionsByDay.computeIfAbsent(day, key -> new TreeMap<>()).putIfAbsent(number, label);
            }
        }
        Map<LocalDate, Boolean> attended = new HashMap<>();
        for (AttendanceLog log : AttendanceLog.<AttendanceLog>list("student.id = ?1", ownerId)) {
            LocalDate day = GymTimes.toLocalDate(log.checkInTime);
            if (!day.isBefore(start) && !day.isAfter(end)) {
                attended.put(day, true);
            }
        }
        List<CalendarDayResponse> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            List<CalendarSessionResponse> sessions = sessionsByDay.getOrDefault(day, Map.of()).entrySet().stream()
                    .map(entry -> new CalendarSessionResponse(entry.getKey(), entry.getValue()))
                    .toList();
            days.add(new CalendarDayResponse(
                    day,
                    attended.containsKey(day),
                    sets.containsKey(day),
                    sets.getOrDefault(day, 0),
                    sessions
            ));
        }
        return days;
    }

    public List<StudentFollowResponse> followStudents() {
        LocalDate today = GymTimes.today();
        Instant since = GymTimes.startOfDay(today.minusDays(29));
        List<User> students = User.list(
                "deletedAt is null and role in ?1 order by lastName, firstName",
                List.of(Role.USER, Role.ALUMNO)
        );
        List<StudentRoutineAssignment> assignments = StudentRoutineAssignment.list("active = true");
        List<AttendanceLog> recent = AttendanceLog.list("checkInTime >= ?1", since);

        return students.stream().map(student -> {
            List<StudentRoutineAssignment> ownActive = assignments.stream()
                    .filter(item -> item.student.id.equals(student.id) && item.active && !item.isExpired(today))
                    .toList();
            StudentRoutineAssignment assignment = ownActive.stream()
                    .filter(item -> !item.startDate.isAfter(today))
                    .min(Comparator.comparing((StudentRoutineAssignment item) -> item.routine.customStudent)
                            .thenComparing(item -> item.startDate, Comparator.reverseOrder()))
                    .orElseGet(() -> ownActive.stream()
                            .min(Comparator.comparing(item -> item.startDate))
                            .orElse(null));
            List<AttendanceLog> own = recent.stream()
                    .filter(log -> log.student.id.equals(student.id))
                    .toList();
            Instant last = own.stream()
                    .map(log -> log.checkInTime)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            long days = own.stream().map(log -> GymTimes.toLocalDate(log.checkInTime)).distinct().count();
            return new StudentFollowResponse(
                    student.id,
                    student.firstName,
                    student.lastName,
                    student.email,
                    assignment == null ? null : assignment.routine.title,
                    assignment == null ? null : assignment.startDate,
                    assignment == null ? null : assignment.expirationDate,
                    assignment != null && assignment.routine.customStudent,
                    last,
                    days
            );
        }).toList();
    }

    private String owner(User actor, String studentId) {
        if (actor.role.isStudent()) {
            if (studentId != null && !studentId.isBlank() && !studentId.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés ver el progreso de otra persona");
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

    private static final class DayBucket {
        private BigDecimal maxWeight = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
        private int sets;
    }
}
