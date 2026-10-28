package com.authbase.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "gym_opening_hours")
public class GymOpeningHour extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    /** ISO: 1 = lunes … 7 = domingo. */
    @Column(name = "day_of_week", nullable = false)
    public int dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    public LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    public LocalTime closesAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
