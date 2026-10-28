package com.authbase.gym;

import com.authbase.error.ApiException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class GymTimes {

    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private GymTimes() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    public static String format(LocalTime time) {
        return time == null ? null : time.format(HM);
    }

    public static LocalTime parse(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("HORA_INVALIDA", "La " + label + " es obligatoria");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("HORA_INVALIDA", "La " + label + " no es válida");
        }
    }

    public static void requireRange(LocalTime start, LocalTime end, String message) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("HORARIO_INVALIDO", message);
        }
    }

    public static int requireDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw ApiException.badRequest("DIA_INVALIDO", "El día tiene que estar entre 1 (lunes) y 7 (domingo)");
        }
        return dayOfWeek;
    }

    public static LocalDate parseMonth(Integer year, Integer month) {
        LocalDate today = today();
        int y = year == null ? today.getYear() : year;
        int m = month == null ? today.getMonthValue() : month;
        try {
            return LocalDate.of(y, m, 1);
        } catch (DateTimeException ex) {
            throw ApiException.badRequest("MES_INVALIDO", "El mes no es válido");
        }
    }
}
