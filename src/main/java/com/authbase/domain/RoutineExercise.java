package com.authbase.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
public class RoutineExercise extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id", nullable = false)
    public Routine routine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    public Exercise exercise;

    @Column(name = "order_index", nullable = false)
    public int orderIndex;

    @Column(name = "target_sets", nullable = false)
    public int targetSets;

    @Column(name = "target_reps", nullable = false)
    public int targetReps;

    @Column(name = "target_rest_seconds", nullable = false)
    public int targetRestSeconds;

    @Column(name = "notes", length = 500)
    public String notes;

    @Column(name = "day_number")
    public Integer dayNumber;

    @Column(name = "day_label", length = 80)
    public String dayLabel;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
