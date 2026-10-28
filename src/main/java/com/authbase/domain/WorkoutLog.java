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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workout_logs")
public class WorkoutLog extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    public User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    public Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    public Routine routine;

    @Column(name = "logged_at", nullable = false)
    public Instant loggedAt;

    @Column(name = "set_number", nullable = false)
    public int setNumber;

    @Column(name = "reps_completed", nullable = false)
    public int repsCompleted;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    public BigDecimal weightKg;

    @Column(name = "rpe")
    public Integer rpe;

    @Column(name = "day_number")
    public Integer dayNumber;

    @Column(name = "day_label", length = 80)
    public String dayLabel;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (loggedAt == null) {
            loggedAt = Instant.now();
        }
    }
}
