package com.authbase.service;

import com.authbase.api.dto.GymDtos.ExerciseResponse;
import com.authbase.api.dto.GymDtos.UpsertExerciseRequest;
import com.authbase.domain.Exercise;
import com.authbase.domain.MuscleGroup;
import com.authbase.domain.RoutineExercise;
import com.authbase.domain.WorkoutLog;
import com.authbase.error.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class ExerciseService {

    public List<ExerciseResponse> list(String q, MuscleGroup muscleGroup) {
        StringBuilder where = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (q != null && !q.isBlank()) {
            where.append(" and lower(name) like :q");
            params.put("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (muscleGroup != null) {
            where.append(" and muscleGroup = :muscleGroup");
            params.put("muscleGroup", muscleGroup);
        }
        return Exercise.<Exercise>find(where + " order by muscleGroup, lower(name)", params)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ExerciseResponse get(String id) {
        return toResponse(require(id));
    }

    @Transactional
    public ExerciseResponse create(UpsertExerciseRequest request) {
        Exercise exercise = new Exercise();
        apply(exercise, request);
        exercise.persist();
        return toResponse(exercise);
    }

    @Transactional
    public ExerciseResponse update(String id, UpsertExerciseRequest request) {
        Exercise exercise = require(id);
        apply(exercise, request);
        return toResponse(exercise);
    }

    @Transactional
    public void delete(String id) {
        Exercise exercise = require(id);
        if (RoutineExercise.count("exercise.id = ?1", id) > 0 || WorkoutLog.count("exercise.id = ?1", id) > 0) {
            throw ApiException.conflict(
                    "EJERCICIO_EN_USO",
                    "No se puede eliminar el ejercicio porque está en uso"
            );
        }
        exercise.delete();
    }

    public Exercise require(String id) {
        Exercise exercise = Exercise.findById(id);
        if (exercise == null) {
            throw ApiException.notFound("EJERCICIO_NO_ENCONTRADO", "El ejercicio no existe");
        }
        return exercise;
    }

    private void apply(Exercise exercise, UpsertExerciseRequest request) {
        exercise.name = request.name().trim();
        exercise.description = blankToNull(request.description());
        exercise.muscleGroup = request.muscleGroup();
        String video = blankToNull(request.videoUrl());
        if (video != null && !(video.startsWith("http://") || video.startsWith("https://"))) {
            throw ApiException.badRequest(
                    "VIDEO_INVALIDO",
                    "El enlace del video tiene que empezar con http:// o https://"
            );
        }
        exercise.videoUrl = video;
    }

    private ExerciseResponse toResponse(Exercise exercise) {
        return new ExerciseResponse(
                exercise.id,
                exercise.name,
                exercise.description,
                exercise.muscleGroup,
                exercise.videoUrl,
                exercise.createdAt
        );
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
