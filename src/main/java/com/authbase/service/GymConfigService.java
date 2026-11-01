package com.authbase.service;

import com.authbase.api.dto.GymDtos.GymConfigResponse;
import com.authbase.api.dto.GymDtos.OccupancyResponse;
import com.authbase.api.dto.GymDtos.OpeningHourInput;
import com.authbase.api.dto.GymDtos.OpeningHourResponse;
import com.authbase.api.dto.GymDtos.PublicGymResponse;
import com.authbase.api.dto.GymDtos.UpdateGymConfigRequest;
import com.authbase.domain.AttendanceLog;
import com.authbase.domain.GymConfig;
import com.authbase.domain.GymOpeningHour;
import com.authbase.domain.GymTheme;
import com.authbase.domain.OperatingMode;
import com.authbase.error.ApiException;
import com.authbase.gym.GymTimes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class GymConfigService {

    public GymConfigResponse get() {
        return toResponse(GymConfig.current());
    }

    @Transactional
    public GymConfigResponse update(UpdateGymConfigRequest request) {
        LocalTime opening = GymTimes.parse(request.openingTime(), "hora de apertura");
        LocalTime closing = GymTimes.parse(request.closingTime(), "hora de cierre");
        GymTimes.requireRange(opening, closing, "El horario de cierre debe ser posterior al de apertura");

        GymConfig config = GymConfig.current();
        config.operatingMode = request.operatingMode();
        config.openingTime = opening;
        config.closingTime = closing;
        config.maxCapacityPerSlot = request.maxCapacityPerSlot();
        String name = ExerciseService.blankToNull(request.name());
        config.gymName = name == null ? "Gimnasio" : name;
        config.address = ExerciseService.blankToNull(request.address());
        config.mapUrl = ExerciseService.blankToNull(request.mapUrl());
        if (request.description() != null) {
            config.description = ExerciseService.blankToNull(request.description());
        }
        config.theme = request.theme() == null ? GymTheme.ENERGIA : request.theme();
        if (request.hours() != null) {
            replaceHours(config, request.hours());
        }
        return toResponse(config);
    }

    public boolean isOpenNow() {
        return isOpenAt(GymTimes.today(), LocalTime.now(GymTimes.ZONE));
    }

    public boolean opensOn(LocalDate date) {
        if (GymOpeningHour.count() == 0) {
            return true;
        }
        return GymOpeningHour.count("dayOfWeek = ?1", date.getDayOfWeek().getValue()) > 0;
    }

    public boolean isOpenAt(LocalDate date, LocalTime time) {
        int day = date.getDayOfWeek().getValue();
        List<GymOpeningHour> rows = GymOpeningHour.list("dayOfWeek = ?1", day);
        if (rows.isEmpty()) {
            if (GymOpeningHour.count() == 0) {
                GymConfig config = GymConfig.current();
                return !time.isBefore(config.openingTime) && time.isBefore(config.closingTime);
            }
            return false;
        }
        for (GymOpeningHour row : rows) {
            if (!time.isBefore(row.opensAt) && time.isBefore(row.closesAt)) {
                return true;
            }
        }
        return false;
    }

    public PublicGymResponse publicInfo() {
        GymConfig config = GymConfig.current();
        return new PublicGymResponse(
                displayName(config),
                GymTimes.format(config.openingTime),
                GymTimes.format(config.closingTime),
                config.address,
                config.mapUrl,
                config.theme == null ? GymTheme.ENERGIA : config.theme,
                listHours(),
                config.description
        );
    }

    public OccupancyResponse occupancy() {
        GymConfig config = GymConfig.current();
        LocalDate today = GymTimes.today();
        long inside = AttendanceLog.count(
                "checkOutTime is null and checkInTime >= ?1 and checkInTime < ?2",
                GymTimes.startOfDay(today),
                GymTimes.endOfDay(today)
        );
        boolean openNow = isOpenNow();
        return new OccupancyResponse(
                config.operatingMode,
                (int) inside,
                config.maxCapacityPerSlot,
                openNow
        );
    }

    public boolean acceptsBookings(OperatingMode mode) {
        return mode == OperatingMode.CLASS_SCHEDULED || mode == OperatingMode.HYBRID;
    }

    public boolean enforcesFloorCapacity(OperatingMode mode) {
        return mode == OperatingMode.FREE_ACCESS || mode == OperatingMode.HYBRID;
    }

    private GymConfigResponse toResponse(GymConfig config) {
        return new GymConfigResponse(
                config.operatingMode,
                GymTimes.format(config.openingTime),
                GymTimes.format(config.closingTime),
                config.maxCapacityPerSlot,
                displayName(config),
                config.address,
                config.mapUrl,
                config.theme == null ? GymTheme.ENERGIA : config.theme,
                listHours(),
                config.description
        );
    }

    private void replaceHours(GymConfig config, List<OpeningHourInput> hours) {
        List<GymOpeningHour> next = new ArrayList<>();
        for (OpeningHourInput input : hours) {
            int day = GymTimes.requireDayOfWeek(input.dayOfWeek());
            String dayName = DAY_NAMES[day];
            LocalTime opens = GymTimes.parse(input.opensAt(), "apertura del " + dayName);
            LocalTime closes = GymTimes.parse(input.closesAt(), "cierre del " + dayName);
            GymTimes.requireRange(opens, closes, "El " + dayName + " cierra antes de abrir");
            GymOpeningHour row = new GymOpeningHour();
            row.dayOfWeek = day;
            row.opensAt = opens;
            row.closesAt = closes;
            next.add(row);
        }
        if (next.isEmpty()) {
            throw ApiException.badRequest("HORARIO_INVALIDO", "Dejá al menos un día abierto");
        }
        next.sort(Comparator.comparingInt((GymOpeningHour row) -> row.dayOfWeek).thenComparing(row -> row.opensAt));
        for (int i = 0; i < next.size(); i++) {
            GymOpeningHour current = next.get(i);
            int sameDay = 1;
            for (int j = i + 1; j < next.size() && next.get(j).dayOfWeek == current.dayOfWeek; j++) {
                sameDay++;
                if (!next.get(j).opensAt.isAfter(next.get(j - 1).closesAt)) {
                    throw ApiException.badRequest(
                            "HORARIO_INVALIDO",
                            "Los turnos del " + DAY_NAMES[current.dayOfWeek] + " se superponen"
                    );
                }
            }
            if (sameDay > 2) {
                throw ApiException.badRequest(
                        "HORARIO_INVALIDO",
                        "Cada día admite hasta dos turnos, por ejemplo mañana y tarde"
                );
            }
        }
        GymOpeningHour.deleteAll();
        LocalTime earliest = null;
        LocalTime latest = null;
        for (GymOpeningHour row : next) {
            row.persist();
            if (earliest == null || row.opensAt.isBefore(earliest)) {
                earliest = row.opensAt;
            }
            if (latest == null || row.closesAt.isAfter(latest)) {
                latest = row.closesAt;
            }
        }
        config.openingTime = earliest;
        config.closingTime = latest;
    }

    private List<OpeningHourResponse> listHours() {
        List<GymOpeningHour> rows = GymOpeningHour.list("order by dayOfWeek asc, opensAt asc");
        return rows.stream()
                .map(row -> new OpeningHourResponse(
                        row.dayOfWeek,
                        GymTimes.format(row.opensAt),
                        GymTimes.format(row.closesAt)
                ))
                .toList();
    }

    private static final String[] DAY_NAMES = {
            "", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"
    };

    private String displayName(GymConfig config) {
        if (config.gymName == null || config.gymName.isBlank()) {
            return "Gimnasio";
        }
        return config.gymName;
    }
}
