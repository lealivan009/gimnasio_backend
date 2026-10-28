package com.authbase.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "gym_config")
public class GymConfig extends PanacheEntityBase {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id = DEFAULT_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", nullable = false, length = 32)
    public OperatingMode operatingMode = OperatingMode.FREE_ACCESS;

    @Column(name = "opening_time", nullable = false)
    public LocalTime openingTime = LocalTime.of(7, 0);

    @Column(name = "closing_time", nullable = false)
    public LocalTime closingTime = LocalTime.of(22, 0);

    @Column(name = "max_capacity_per_slot", nullable = false)
    public int maxCapacityPerSlot = 30;

    @Column(name = "gym_name", length = 120)
    public String gymName;

    @Column(name = "address", length = 200)
    public String address;

    @Column(name = "description", length = 1000)
    public String description;

    @Column(name = "map_url", length = 500)
    public String mapUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 32)
    public GymTheme theme = GymTheme.ENERGIA;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null || id.isBlank()) {
            id = DEFAULT_ID;
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static GymConfig current() {
        GymConfig config = findById(DEFAULT_ID);
        if (config == null) {
            throw new IllegalStateException("Gym config not initialized");
        }
        return config;
    }
}
