package com.authbase.service;

import com.authbase.api.dto.GymDtos.ActiveRoutineResponse;
import com.authbase.api.dto.GymDtos.AssignRoutineRequest;
import com.authbase.api.dto.GymDtos.AssignmentResponse;
import com.authbase.api.dto.GymDtos.UpdateAssignmentRequest;
import com.authbase.api.dto.GymDtos.CloneRoutineRequest;
import com.authbase.api.dto.GymDtos.RoutineExerciseInput;
import com.authbase.api.dto.GymDtos.RoutineExerciseResponse;
import com.authbase.api.dto.GymDtos.RoutineResponse;
import com.authbase.api.dto.GymDtos.UpsertRoutineRequest;
import com.authbase.domain.Exercise;
import com.authbase.domain.Routine;
import com.authbase.domain.RoutineExercise;
import com.authbase.domain.StudentRoutineAssignment;
import com.authbase.domain.User;
import com.authbase.domain.WorkoutLog;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
public class RoutineService {

    private final ExerciseService exerciseService;

    public RoutineService(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    public List<RoutineResponse> list(User actor) {
        List<Routine> routines = actor.role.isStaff()
                ? Routine.list("customStudent = false order by lower(title)")
                : Routine.list("customStudent = true and createdBy.id = ?1 order by lower(title)", actor.id);
        return routines.stream().map(this::toResponse).toList();
    }

    public RoutineResponse get(User actor, String id) {
        return toResponse(requireVisible(actor, id));
    }

    @Transactional
    public RoutineResponse create(User actor, UpsertRoutineRequest request) {
        boolean custom = actor.role.isStudent();
        if (!custom && !actor.role.isStaff()) {
            throw ApiException.forbidden("FORBIDDEN", "No tenés permiso para crear rutinas");
        }
        Routine routine = new Routine();
        routine.createdBy = actor;
        routine.customStudent = custom;
        apply(routine, request);
        routine.persist();
        replaceExercises(routine, request.exercises());
        if (custom) {
            LocalDate start = request.startDate() == null ? GymTimes.today() : request.startDate();
            assignTo(routine, actor, start, request.expirationDate());
        }
        return toResponse(routine);
    }

    @Transactional
    public RoutineResponse update(User actor, String id, UpsertRoutineRequest request) {
        Routine routine = requireEditable(actor, id);
        apply(routine, request);
        replaceExercises(routine, request.exercises());
        return toResponse(routine);
    }

    @Transactional
    public void delete(User actor, String id) {
        Routine routine = requireEditable(actor, id);
        if (WorkoutLog.count("routine.id = ?1", id) > 0) {
            throw ApiException.conflict(
                    "RUTINA_CON_REGISTROS",
                    "No se puede eliminar la rutina porque tiene entrenamientos registrados"
            );
        }
        long assignedToOthers = StudentRoutineAssignment.count(
                "routine.id = ?1 and student.id <> ?2",
                id,
                actor.id
        );
        if (assignedToOthers > 0) {
            throw ApiException.conflict(
                    "RUTINA_ASIGNADA",
                    "No se puede eliminar la rutina porque tiene alumnos asignados"
            );
        }
        StudentRoutineAssignment.delete("routine.id = ?1", id);
        RoutineExercise.delete("routine.id = ?1", id);
        routine.delete();
    }

    @Transactional
    public RoutineResponse clone(User actor, String id, CloneRoutineRequest request) {
        Routine source = requireVisible(actor, id);
        Routine copy = new Routine();
        copy.createdBy = actor;
        copy.customStudent = actor.role.isStudent();
        String title = request != null && request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : "Copia de " + source.title;
        if (title.length() > 120) {
            title = title.substring(0, 120);
        }
        copy.title = title;
        copy.description = source.description;
        copy.persist();

        List<RoutineExercise> exercises = exercisesOf(source.id);
        for (RoutineExercise exercise : exercises) {
            RoutineExercise clone = new RoutineExercise();
            clone.routine = copy;
            clone.exercise = exercise.exercise;
            clone.orderIndex = exercise.orderIndex;
            clone.targetSets = exercise.targetSets;
            clone.targetReps = exercise.targetReps;
            clone.targetRestSeconds = exercise.targetRestSeconds;
            clone.notes = exercise.notes;
            clone.dayNumber = exercise.dayNumber;
            clone.dayLabel = exercise.dayLabel;
            clone.persist();
        }
        if (copy.customStudent) {
            assignTo(copy, actor, GymTimes.today(), null);
        }
        return toResponse(copy);
    }

    @Transactional
    public AssignmentResponse assign(User actor, String routineId, AssignRoutineRequest request) {
        Routine routine = requireVisible(actor, routineId);
        User student = requireStudent(request.studentId());
        if (actor.role.isStudent()) {
            if (!actor.id.equals(student.id) || !routine.customStudent || !routine.createdBy.id.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés asignar esta rutina");
            }
        }
        return assignTo(routine, student, request.startDate(), request.expirationDate());
    }

    @Transactional
    public AssignmentResponse updateAssignment(User actor, String id, UpdateAssignmentRequest request) {
        StudentRoutineAssignment assignment = requireAssignment(id);
        requireCanManage(actor, assignment);
        validateDates(request.startDate(), request.expirationDate());
        assignment.startDate = request.startDate();
        assignment.expirationDate = request.expirationDate();
        boolean makeActive = request.active() == null || request.active();
        if (makeActive) {
            deactivateOthers(assignment.student.id, assignment.id);
            assignment.active = true;
        } else {
            assignment.active = false;
        }
        return toAssignment(assignment, GymTimes.today());
    }

    @Transactional
    public void deleteAssignment(User actor, String id) {
        StudentRoutineAssignment assignment = requireAssignment(id);
        requireCanManage(actor, assignment);
        assignment.delete();
    }

    public List<AssignmentResponse> listAssignments(User actor, String studentId, Boolean activeOnly) {
        String ownerId;
        if (actor.role.isStudent()) {
            if (studentId != null && !studentId.isBlank() && !studentId.equals(actor.id)) {
                throw ApiException.forbidden("FORBIDDEN", "No podés ver las rutinas de otra persona");
            }
            ownerId = actor.id;
        } else if (studentId == null || studentId.isBlank()) {
            ownerId = null;
        } else {
            ownerId = studentId;
        }

        String query = ownerId == null ? "order by startDate desc" : "student.id = ?1 order by startDate desc";
        List<StudentRoutineAssignment> assignments = ownerId == null
                ? StudentRoutineAssignment.list(query)
                : StudentRoutineAssignment.list(query, ownerId);
        LocalDate today = GymTimes.today();
        return assignments.stream()
                .filter(item -> activeOnly == null || !activeOnly || item.active)
                .map(item -> toAssignment(item, today))
                .toList();
    }

    public ActiveRoutineResponse activeFor(User actor) {
        if (!actor.role.isStudent()) {
            return new ActiveRoutineResponse(null, null, null);
        }
        LocalDate today = GymTimes.today();
        List<StudentRoutineAssignment> assignments = StudentRoutineAssignment.list(
                "student.id = ?1 order by startDate desc",
                actor.id
        );
        StudentRoutineAssignment current = assignments.stream()
                .filter(item -> item.isCurrent(today))
                .min(Comparator.comparing((StudentRoutineAssignment item) -> item.routine.customStudent)
                        .thenComparing(item -> item.startDate, Comparator.reverseOrder()))
                .orElse(null);
        if (current != null) {
            return new ActiveRoutineResponse(toAssignment(current, today), toResponse(current.routine), null);
        }
        StudentRoutineAssignment expired = assignments.stream()
                .filter(item -> item.isExpired(today))
                .findFirst()
                .orElse(null);
        return new ActiveRoutineResponse(
                null,
                null,
                expired == null ? null : toAssignment(expired, today)
        );
    }

    public void ensureLoggable(User student, String routineId, LocalDate onDate) {
        if (routineId == null || routineId.isBlank()) {
            return;
        }
        LocalDate date = onDate == null ? GymTimes.today() : onDate;
        List<StudentRoutineAssignment> assignments = StudentRoutineAssignment.list(
                "student.id = ?1 and routine.id = ?2",
                student.id,
                routineId
        );
        if (assignments.isEmpty()) {
            throw ApiException.notFound("RUTINA_NO_ASIGNADA", "La rutina seleccionada no está asignada");
        }
        boolean covers = assignments.stream().anyMatch(item -> covers(item, date));
        if (!covers) {
            throw ApiException.badRequest(
                    "RUTINA_NO_VIGENTE",
                    "La rutina seleccionada no estaba vigente en la fecha ingresada"
            );
        }
    }

    private boolean covers(StudentRoutineAssignment item, LocalDate date) {
        if (date.isBefore(item.startDate)) {
            return false;
        }
        return item.expirationDate == null || !date.isAfter(item.expirationDate);
    }

    private AssignmentResponse assignTo(
            Routine routine,
            User student,
            LocalDate startDate,
            LocalDate expirationDate
    ) {
        validateDates(startDate, expirationDate);
        StudentRoutineAssignment same = StudentRoutineAssignment.find(
                "student.id = ?1 and routine.id = ?2 order by createdAt desc",
                student.id,
                routine.id
        ).firstResult();
        if (same != null) {
            deactivateOthers(student.id, same.id);
            same.startDate = startDate;
            same.expirationDate = expirationDate;
            same.active = true;
            return toAssignment(same, GymTimes.today());
        }
        deactivateOthers(student.id, null);
        StudentRoutineAssignment assignment = new StudentRoutineAssignment();
        assignment.student = student;
        assignment.routine = routine;
        assignment.startDate = startDate;
        assignment.expirationDate = expirationDate;
        assignment.active = true;
        assignment.persist();
        return toAssignment(assignment, GymTimes.today());
    }

    private void validateDates(LocalDate startDate, LocalDate expirationDate) {
        if (expirationDate != null && expirationDate.isBefore(startDate)) {
            throw ApiException.badRequest(
                    "FECHAS_INVALIDAS",
                    "La fecha de vencimiento no puede ser anterior al inicio"
            );
        }
    }

    private void deactivateOthers(String studentId, String keepId) {
        if (keepId == null) {
            StudentRoutineAssignment.update("active = false where student.id = ?1 and active = true", studentId);
            return;
        }
        StudentRoutineAssignment.update(
                "active = false where student.id = ?1 and active = true and id <> ?2",
                studentId,
                keepId
        );
    }

    private StudentRoutineAssignment requireAssignment(String id) {
        StudentRoutineAssignment assignment = StudentRoutineAssignment.findById(id);
        if (assignment == null) {
            throw ApiException.notFound("ASIGNACION_NO_ENCONTRADA", "La asignación no existe");
        }
        return assignment;
    }

    private void requireCanManage(User actor, StudentRoutineAssignment assignment) {
        if (actor.role.isStaff() || actor.id.equals(assignment.student.id)) {
            return;
        }
        throw ApiException.forbidden("FORBIDDEN", "No podés modificar esta asignación");
    }

    private void apply(Routine routine, UpsertRoutineRequest request) {
        routine.title = request.title().trim();
        routine.description = ExerciseService.blankToNull(request.description());
    }

    private void replaceExercises(Routine routine, List<RoutineExerciseInput> inputs) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        for (RoutineExerciseInput input : inputs) {
            int number = dayNumber(input.dayNumber());
            labels.putIfAbsent(number, ExerciseService.blankToNull(input.dayLabel()));
        }
        if (labels.size() > 1) {
            for (String label : labels.values()) {
                if (label == null) {
                    throw ApiException.badRequest(
                            "DIA_SIN_NOMBRE",
                            "Cada día de la rutina necesita un nombre, por ejemplo Pecho y bíceps"
                    );
                }
            }
        }
        if (routine.id != null) {
            RoutineExercise.delete("routine.id = ?1", routine.id);
            RoutineExercise.flush();
        }
        int index = 0;
        for (RoutineExerciseInput input : inputs) {
            Exercise exercise = exerciseService.require(input.exerciseId());
            int number = dayNumber(input.dayNumber());
            RoutineExercise item = new RoutineExercise();
            item.routine = routine;
            item.exercise = exercise;
            item.orderIndex = input.orderIndex() >= 0 ? input.orderIndex() : index;
            item.targetSets = input.targetSets();
            item.targetReps = input.targetReps();
            item.targetRestSeconds = input.targetRestSeconds();
            item.notes = ExerciseService.blankToNull(input.notes());
            item.dayNumber = number;
            item.dayLabel = labels.get(number);
            item.persist();
            index++;
        }
    }

    private int dayNumber(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private Routine requireVisible(User actor, String id) {
        Routine routine = Routine.findById(id);
        if (routine == null) {
            throw ApiException.notFound("RUTINA_NO_ENCONTRADA", "La rutina no existe");
        }
        if (actor.role.isStaff()) {
            return routine;
        }
        if (routine.customStudent && routine.createdBy.id.equals(actor.id)) {
            return routine;
        }
        long assigned = StudentRoutineAssignment.count("routine.id = ?1 and student.id = ?2", id, actor.id);
        if (assigned == 0) {
            throw ApiException.forbidden("FORBIDDEN", "No podés ver esta rutina");
        }
        return routine;
    }

    private Routine requireEditable(User actor, String id) {
        Routine routine = requireVisible(actor, id);
        if (routine.customStudent && !routine.createdBy.id.equals(actor.id)) {
            throw ApiException.forbidden("FORBIDDEN", "No podés modificar la rutina de un alumno");
        }
        if (!routine.customStudent && actor.role.isStudent()) {
            throw ApiException.forbidden("FORBIDDEN", "No podés modificar una rutina del profesor");
        }
        return routine;
    }

    private User requireStudent(String id) {
        User user = User.findActiveById(id)
                .orElseThrow(() -> ApiException.notFound("ALUMNO_NO_ENCONTRADO", "El alumno no existe"));
        if (!user.role.isStudent()) {
            throw ApiException.badRequest("NO_ES_ALUMNO", "Solo se pueden asignar rutinas a alumnos");
        }
        return user;
    }

    private List<RoutineExercise> exercisesOf(String routineId) {
        return RoutineExercise.list("routine.id = ?1 order by coalesce(dayNumber, 1), orderIndex", routineId);
    }

    private RoutineResponse toResponse(Routine routine) {
        List<RoutineExerciseResponse> exercises = exercisesOf(routine.id).stream()
                .map(item -> new RoutineExerciseResponse(
                        item.id,
                        item.exercise.id,
                        item.exercise.name,
                        item.exercise.muscleGroup,
                        item.orderIndex,
                        item.targetSets,
                        item.targetReps,
                        item.targetRestSeconds,
                        item.notes,
                        item.dayNumber == null || item.dayNumber < 1 ? 1 : item.dayNumber,
                        item.dayLabel
                ))
                .toList();
        return new RoutineResponse(
                routine.id,
                routine.title,
                routine.description,
                routine.createdBy.id,
                routine.createdBy.displayName(),
                routine.customStudent,
                exercises
        );
    }

    private AssignmentResponse toAssignment(StudentRoutineAssignment assignment, LocalDate today) {
        return new AssignmentResponse(
                assignment.id,
                assignment.student.id,
                assignment.student.displayName(),
                assignment.routine.id,
                assignment.routine.title,
                assignment.routine.customStudent,
                assignment.startDate,
                assignment.expirationDate,
                assignment.active,
                assignment.isExpired(today)
        );
    }
}
