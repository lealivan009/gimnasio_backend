package com.authbase;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class GymResourceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void studentCannotCreateExercises() {
        String token = register("alumno-" + System.nanoTime() + "@example.com");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Sentadilla",
                          "muscleGroup": "PIERNAS"
                        }
                        """)
                .when()
                .post("/api/ejercicios")
                .then()
                .statusCode(403);
    }

    @Test
    void bookingRejectsFullClassInSpanish() {
        String admin = login("admin@example.com", "Admin1234!");
        String adminId = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        LocalDate date = LocalDate.now(ZONE);
        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "operatingMode": "CLASS_SCHEDULED",
                          "openingTime": "06:00",
                          "closingTime": "23:00",
                          "maxCapacityPerSlot": 20
                        }
                        """)
                .when()
                .put("/api/gimnasio/config")
                .then()
                .statusCode(200)
                .body("operatingMode", equalTo("CLASS_SCHEDULED"));

        String classId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Funcional",
                          "instructorId": "%s",
                          "dayOfWeek": %d,
                          "startTime": "18:00",
                          "endTime": "19:00",
                          "capacity": 1,
                          "active": true
                        }
                        """.formatted(adminId, date.getDayOfWeek().getValue()))
                .when()
                .post("/api/clases")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        String first = register("cupo-a-" + System.nanoTime() + "@example.com");
        String second = register("cupo-b-" + System.nanoTime() + "@example.com");

        given()
                .header("Authorization", "Bearer " + first)
                .contentType(ContentType.JSON)
                .body("{\"date\":\"%s\"}".formatted(date))
                .when()
                .post("/api/clases/" + classId + "/reservas")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMADA"));

        given()
                .header("Authorization", "Bearer " + second)
                .contentType(ContentType.JSON)
                .body("{\"date\":\"%s\"}".formatted(date))
                .when()
                .post("/api/clases/" + classId + "/reservas")
                .then()
                .statusCode(409)
                .body("message", equalTo("El turno seleccionado no tiene cupos disponibles"));
    }

    @Test
    void expiredRoutineCannotBeLogged() {
        String admin = login("admin@example.com", "Admin1234!");
        String exerciseId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Press banca %s",
                          "description": "Banco plano",
                          "muscleGroup": "PECHO"
                        }
                        """.formatted(System.nanoTime()))
                .when()
                .post("/api/ejercicios")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .extract()
                .path("id");

        String routineId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Fuerza",
                          "description": "Torso",
                          "exercises": [
                            {
                              "exerciseId": "%s",
                              "orderIndex": 0,
                              "targetSets": 3,
                              "targetReps": 8,
                              "targetRestSeconds": 90,
                              "notes": "Controlá la bajada"
                            }
                          ]
                        }
                        """.formatted(exerciseId))
                .when()
                .post("/api/rutinas")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        String email = "vence-" + System.nanoTime() + "@example.com";
        register(email);
        String studentId = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/users?q=" + email)
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");

        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "studentId": "%s",
                          "startDate": "%s",
                          "expirationDate": "%s"
                        }
                        """.formatted(studentId, yesterday, yesterday))
                .when()
                .post("/api/rutinas/" + routineId + "/asignar")
                .then()
                .statusCode(200);

        String student = login(email, "Secret123");
        given()
                .header("Authorization", "Bearer " + student)
                .when()
                .get("/api/rutinas/activa")
                .then()
                .statusCode(200)
                .body("expired.routineTitle", equalTo("Fuerza"))
                .body("assignment", equalTo(null));

        given()
                .header("Authorization", "Bearer " + student)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exerciseId": "%s",
                          "routineId": "%s",
                          "setNumber": 1,
                          "repsCompleted": 8,
                          "weightKg": 40
                        }
                        """.formatted(exerciseId, routineId))
                .when()
                .post("/api/registros-entrenamiento")
                .then()
                .statusCode(400)
                .body("message", equalTo("La rutina seleccionada no estaba vigente en la fecha ingresada"));
    }

    @Test
    void reassignUpdatesTheSameAssignmentAndUpcomingRoutineShowsUp() {
        String admin = login("admin@example.com", "Admin1234!");
        String exerciseId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Remo %s",
                          "muscleGroup": "ESPALDA"
                        }
                        """.formatted(System.nanoTime()))
                .when()
                .post("/api/ejercicios")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        String routineId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Fuerza espalda",
                          "exercises": [
                            {
                              "exerciseId": "%s",
                              "orderIndex": 0,
                              "targetSets": 3,
                              "targetReps": 8,
                              "targetRestSeconds": 60
                            }
                          ]
                        }
                        """.formatted(exerciseId))
                .when()
                .post("/api/rutinas")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        String email = "proxima-" + System.nanoTime() + "@example.com";
        register(email);
        String studentId = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/users?q=" + email)
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");

        LocalDate start = LocalDate.now(ZONE).plusDays(5);
        LocalDate end = start.plusDays(10);
        String assignmentId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "studentId": "%s",
                          "startDate": "%s",
                          "expirationDate": "%s"
                        }
                        """.formatted(studentId, start, end))
                .when()
                .post("/api/rutinas/" + routineId + "/asignar")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "studentId": "%s",
                          "startDate": "%s",
                          "expirationDate": "%s"
                        }
                        """.formatted(studentId, start, end.plusDays(2)))
                .when()
                .post("/api/rutinas/" + routineId + "/asignar")
                .then()
                .statusCode(200)
                .body("id", equalTo(assignmentId));

        given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/rutinas/asignaciones?studentId=" + studentId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].active", equalTo(true));

        given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/alumnos")
                .then()
                .statusCode(200)
                .body("find { it.email == '%s' }.activeRoutineTitle".formatted(email), equalTo("Fuerza espalda"));

        given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .delete("/api/rutinas/asignaciones/" + assignmentId)
                .then()
                .statusCode(200)
                .body("message", equalTo("Asignación eliminada"));

        given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/alumnos")
                .then()
                .statusCode(200)
                .body("find { it.email == '%s' }.activeRoutineTitle".formatted(email), nullValue());
    }

    @Test
    void splitRoutineAsksWhichDayIsBeingLogged() {
        String admin = login("admin@example.com", "Admin1234!");
        String chestId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Press banca %s",
                          "muscleGroup": "PECHO"
                        }
                        """.formatted(System.nanoTime()))
                .when()
                .post("/api/ejercicios")
                .then()
                .statusCode(200)
                .extract()
                .path("id");
        String backId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Jalon %s",
                          "muscleGroup": "ESPALDA"
                        }
                        """.formatted(System.nanoTime()))
                .when()
                .post("/api/ejercicios")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        String routineId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Torso 2 dias",
                          "exercises": [
                            {
                              "exerciseId": "%s",
                              "orderIndex": 0,
                              "targetSets": 3,
                              "targetReps": 8,
                              "targetRestSeconds": 60,
                              "dayNumber": 1,
                              "dayLabel": "Pecho y bíceps"
                            },
                            {
                              "exerciseId": "%s",
                              "orderIndex": 1,
                              "targetSets": 3,
                              "targetReps": 10,
                              "targetRestSeconds": 60,
                              "dayNumber": 2,
                              "dayLabel": "Espalda y tríceps"
                            }
                          ]
                        }
                        """.formatted(chestId, backId))
                .when()
                .post("/api/rutinas")
                .then()
                .statusCode(200)
                .body("exercises[0].dayLabel", equalTo("Pecho y bíceps"))
                .body("exercises[1].dayNumber", equalTo(2))
                .extract()
                .path("id");

        String email = "split-" + System.nanoTime() + "@example.com";
        register(email);
        String studentId = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/users?q=" + email)
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");

        LocalDate today = LocalDate.now(ZONE);
        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "studentId": "%s",
                          "startDate": "%s"
                        }
                        """.formatted(studentId, today))
                .when()
                .post("/api/rutinas/" + routineId + "/asignar")
                .then()
                .statusCode(200);

        String student = login(email, "Secret123");
        given()
                .header("Authorization", "Bearer " + student)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exerciseId": "%s",
                          "routineId": "%s",
                          "setNumber": 1,
                          "repsCompleted": 8,
                          "weightKg": 20
                        }
                        """.formatted(backId, routineId))
                .when()
                .post("/api/registros-entrenamiento")
                .then()
                .statusCode(400)
                .body("message", equalTo("Elegí qué día de la rutina estás haciendo"));

        List<Map<String, Object>> originalHours = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/gimnasio/config")
                .then()
                .statusCode(200)
                .extract()
                .path("hours");
        List<Map<String, Object>> openAllDay = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            openAllDay.add(Map.of("dayOfWeek", day, "opensAt", "00:00", "closesAt", "23:59"));
        }
        try {
            putHours(admin, openAllDay);
            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .post("/api/asistencia/ingreso")
                    .then()
                    .statusCode(200);

            given()
                    .header("Authorization", "Bearer " + student)
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "exerciseId": "%s",
                              "routineId": "%s",
                              "setNumber": 1,
                              "repsCompleted": 10,
                              "weightKg": 30,
                              "dayNumber": 2
                            }
                            """.formatted(backId, routineId))
                    .when()
                    .post("/api/registros-entrenamiento")
                    .then()
                    .statusCode(200)
                    .body("dayLabel", equalTo("Espalda y tríceps"))
                    .body("dayNumber", equalTo(2));

            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .get("/api/estadisticas/calendario?year=" + today.getYear() + "&month=" + today.getMonthValue())
                    .then()
                    .statusCode(200)
                    .body("find { it.date == '%s' }.sessions[0].label".formatted(today), equalTo("Espalda y tríceps"));
        } finally {
            putHours(admin, originalHours);
        }
    }

    @Test
    void oldWorkoutIsRejectedAndWeightIsFree() {
        String admin = login("admin@example.com", "Admin1234!");
        String exerciseId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Peso muerto %s",
                          "muscleGroup": "PIERNAS"
                        }
                        """.formatted(System.nanoTime()))
                .when()
                .post("/api/ejercicios")
                .then()
                .statusCode(200)
                .extract()
                .path("id");
        String routineId = given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Fuerza corta",
                          "exercises": [
                            {
                              "exerciseId": "%s",
                              "orderIndex": 0,
                              "targetSets": 3,
                              "targetReps": 5,
                              "targetRestSeconds": 90
                            }
                          ]
                        }
                        """.formatted(exerciseId))
                .when()
                .post("/api/rutinas")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        String email = "peso-" + System.nanoTime() + "@example.com";
        register(email);
        String studentId = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/users?q=" + email)
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");
        LocalDate today = LocalDate.now(ZONE);
        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "studentId": "%s",
                          "startDate": "%s"
                        }
                        """.formatted(studentId, today.minusDays(10)))
                .when()
                .post("/api/rutinas/" + routineId + "/asignar")
                .then()
                .statusCode(200);

        String student = login(email, "Secret123");
        given()
                .header("Authorization", "Bearer " + student)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exerciseId": "%s",
                          "routineId": "%s",
                          "setNumber": 1,
                          "repsCompleted": 5,
                          "weightKg": 60,
                          "loggedDate": "%s"
                        }
                        """.formatted(exerciseId, routineId, today.minusDays(8)))
                .when()
                .post("/api/registros-entrenamiento")
                .then()
                .statusCode(400)
                .body("message", equalTo("No puedes registrar entrenamientos con más de una semana de antigüedad"));

        given()
                .header("Authorization", "Bearer " + student)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exerciseId": "%s",
                          "routineId": "%s",
                          "setNumber": 1,
                          "repsCompleted": 5,
                          "weightKg": 60,
                          "loggedDate": "%s"
                        }
                        """.formatted(exerciseId, routineId, today.minusDays(3)))
                .when()
                .post("/api/registros-entrenamiento")
                .then()
                .statusCode(400)
                .body("message", equalTo("Para cargar los ejercicios tenés que marcar el presente de ese día primero"));

        given()
                .header("Authorization", "Bearer " + student)
                .when()
                .post("/api/asistencia/ingreso?fecha=" + today.minusDays(3))
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + student)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exerciseId": "%s",
                          "routineId": "%s",
                          "setNumber": 1,
                          "repsCompleted": 5,
                          "weightKg": 60,
                          "loggedDate": "%s"
                        }
                        """.formatted(exerciseId, routineId, today.minusDays(3)))
                .when()
                .post("/api/registros-entrenamiento")
                .then()
                .statusCode(200);

        List<Map<String, Object>> originalHours = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/gimnasio/config")
                .then()
                .statusCode(200)
                .extract()
                .path("hours");
        List<Map<String, Object>> openAllDay = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            openAllDay.add(Map.of("dayOfWeek", day, "opensAt", "00:00", "closesAt", "23:59"));
        }
        try {
            putHours(admin, openAllDay);

            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .post("/api/asistencia/ingreso")
                    .then()
                    .statusCode(200);

            given()
                    .header("Authorization", "Bearer " + admin)
                    .when()
                    .post("/api/asistencia/ingreso?studentId=" + studentId)
                    .then()
                    .statusCode(409);

            String yesterday = today.minusDays(1).toString();
            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .post("/api/asistencia/ingreso?fecha=" + yesterday)
                    .then()
                    .statusCode(200)
                    .body("checkOutTime", notNullValue());

            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .post("/api/asistencia/ingreso?fecha=" + yesterday)
                    .then()
                    .statusCode(409)
                    .body("message", equalTo("Ese día ya tiene el presente cargado"));

            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .post("/api/asistencia/ingreso?fecha=" + today.minusDays(8))
                    .then()
                    .statusCode(400)
                    .body("message", equalTo("No podés marcar presente con más de una semana de antigüedad"));

            given()
                    .header("Authorization", "Bearer " + student)
                    .when()
                    .post("/api/asistencia/ingreso?fecha=" + today.plusDays(1))
                    .then()
                    .statusCode(400)
                    .body("message", equalTo("No podés marcar presente en un día que todavía no llegó"));

            given()
                    .header("Authorization", "Bearer " + admin)
                    .when()
                    .post("/api/asistencia/ingreso?studentId=" + studentId + "&fecha=" + today.minusDays(2))
                    .then()
                    .statusCode(200)
                    .body("checkOutTime", notNullValue());

            given()
                    .header("Authorization", "Bearer " + student)
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "weightKg": 80.0,
                              "recordedOn": "%s"
                            }
                            """.formatted(yesterday))
                    .when()
                    .post("/api/peso")
                    .then()
                    .statusCode(200)
                    .body("weightKg", equalTo(80.0f));

            given()
                    .header("Authorization", "Bearer " + student)
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "weightKg": 81.5,
                              "recordedOn": "%s"
                            }
                            """.formatted(today.minusDays(20)))
                    .when()
                    .post("/api/peso")
                    .then()
                    .statusCode(200)
                    .body("weightKg", equalTo(81.5f));

            given()
                    .header("Authorization", "Bearer " + student)
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "weightKg": 82.0,
                              "recordedOn": "%s"
                            }
                            """.formatted(today.plusDays(1)))
                    .when()
                    .post("/api/peso")
                    .then()
                    .statusCode(400)
                    .body("message", equalTo("La fecha del peso no puede ser futura"));

            given()
                    .header("Authorization", "Bearer " + student)
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "weightKg": 81.5,
                              "recordedOn": "%s"
                            }
                            """.formatted(today))
                    .when()
                    .post("/api/peso")
                    .then()
                    .statusCode(200)
                    .body("weightKg", equalTo(81.5f));
        } finally {
            putHours(admin, originalHours);
        }

        given()
                .when()
                .get("/api/gimnasio/publico")
                .then()
                .statusCode(200)
                .body("name", equalTo("Gimnasio"))
                .body("theme", equalTo("ENERGIA"))
                .body("hours.find { it.dayOfWeek == 1 }.opensAt", equalTo("07:00"))
                .body("hours.find { it.dayOfWeek == 1 }.closesAt", equalTo("23:00"))
                .body("hours.findAll { it.dayOfWeek == 6 }.size()", equalTo(2))
                .body("hours.findAll { it.dayOfWeek == 7 }.size()", equalTo(0));
    }

    private void putHours(String admin, List<Map<String, Object>> hours) {
        io.restassured.response.Response config = given()
                .header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/gimnasio/config")
                .then()
                .statusCode(200)
                .extract()
                .response();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operatingMode", config.path("operatingMode"));
        body.put("openingTime", config.path("openingTime"));
        body.put("closingTime", config.path("closingTime"));
        body.put("maxCapacityPerSlot", config.path("maxCapacityPerSlot"));
        body.put("name", config.path("name"));
        body.put("address", config.path("address"));
        body.put("mapUrl", config.path("mapUrl"));
        body.put("theme", config.path("theme"));
        body.put("description", config.path("description"));
        body.put("hours", hours);
        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/api/gimnasio/config")
                .then()
                .statusCode(200);
    }

    private String register(String email) {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123",
                          "firstName": "Alumno",
                          "lastName": "Prueba"
                        }
                        """.formatted(email))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(200)
                .extract()
                .path("user.id");
        String admin = login("admin@example.com", "Admin1234!");
        given()
                .header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/users/" + id + "/aprobar")
                .then()
                .statusCode(200);
        return login(email, "Secret123");
    }

    private String login(String email, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }
}
