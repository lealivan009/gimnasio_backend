package com.authbase.service;

import com.authbase.domain.GymConfig;
import com.authbase.domain.GymOpeningHour;
import com.authbase.domain.OperatingMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalTime;

@ApplicationScoped
public class GymConfigBootstrap {

    private static final Logger LOG = Logger.getLogger(GymConfigBootstrap.class);

    @Transactional
    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE + 30) StartupEvent event) {
        if (GymConfig.findById(GymConfig.DEFAULT_ID) == null) {
            GymConfig config = new GymConfig();
            config.id = GymConfig.DEFAULT_ID;
            config.operatingMode = OperatingMode.FREE_ACCESS;
            config.openingTime = LocalTime.of(7, 0);
            config.closingTime = LocalTime.of(23, 0);
            config.maxCapacityPerSlot = 30;
            config.persist();
            LOG.info("Configuración del gimnasio inicial creada");
        }
        ensureDefaultHours();
        ensureDescription();
    }

    private void ensureDescription() {
        GymConfig config = GymConfig.findById(GymConfig.DEFAULT_ID);
        if (config == null || (config.description != null && !config.description.isBlank())) {
            return;
        }
        config.description = "Acá venís a ponerte fuerte. Entrenás con una rutina clara, sumás músculo, energía y una postura que se nota. El cuerpo cambia cuando volvés, no cuando lo pensás.";
        LOG.info("Descripción del gimnasio inicial creada");
    }

    private void ensureDefaultHours() {
        if (GymConfig.findById(GymConfig.DEFAULT_ID) == null || GymOpeningHour.count() > 0) {
            return;
        }
        for (int day = 1; day <= 5; day++) {
            persistHour(day, LocalTime.of(7, 0), LocalTime.of(23, 0));
        }
        persistHour(6, LocalTime.of(9, 0), LocalTime.of(13, 0));
        persistHour(6, LocalTime.of(16, 0), LocalTime.of(21, 0));
        LOG.info("Horario semanal inicial creado");
    }

    private void persistHour(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        GymOpeningHour hour = new GymOpeningHour();
        hour.dayOfWeek = dayOfWeek;
        hour.opensAt = opensAt;
        hour.closesAt = closesAt;
        hour.persist();
    }
}
