package com.authbase.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class WorkoutDurationBackfill {

    private final EntityManager em;

    public WorkoutDurationBackfill(EntityManager em) {
        this.em = em;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        em.createNativeQuery("""
                UPDATE workout_logs
                SET duration_seconds = reps_completed, reps_completed = 0
                WHERE duration_seconds IS NULL
                  AND reps_completed > 0
                  AND exercise_id IN (SELECT id FROM exercises WHERE measure = 'TIEMPO')
                """).executeUpdate();
    }

    @Transactional
    public void moveStoredReps(String exerciseId) {
        em.createNativeQuery("""
                UPDATE workout_logs
                SET duration_seconds = reps_completed, reps_completed = 0
                WHERE exercise_id = ?1
                  AND duration_seconds IS NULL
                  AND reps_completed > 0
                """)
                .setParameter(1, exerciseId)
                .executeUpdate();
    }
}
