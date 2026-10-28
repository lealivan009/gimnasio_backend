package com.authbase;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class SettingsResourceTest {

    @Test
    void publicSettingsAreReadableWithoutAuth() {
        given()
                .when()
                .get("/api/settings/public")
                .then()
                .statusCode(200)
                .body("allowPublicRegistration", equalTo(true))
                .body("passwordMinLength", equalTo(8));
    }

    @Test
    void adminCanDisablePublicRegistration() {
        String adminToken = login("admin@example.com", "Admin1234!");
        Map<String, Object> baseline = currentSettings(adminToken);

        try {
            Map<String, Object> disabled = new HashMap<>(baseline);
            disabled.put("allowPublicRegistration", false);

            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(ContentType.JSON)
                    .body(disabled)
                    .when()
                    .patch("/api/settings")
                    .then()
                    .statusCode(200)
                    .body("allowPublicRegistration", equalTo(false));

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "email": "blocked-%s@example.com",
                              "password": "Secret123",
                              "firstName": "Bloqueado",
                              "lastName": "Registro"
                            }
                            """.formatted(System.nanoTime()))
                    .when()
                    .post("/api/auth/register")
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("REGISTRATION_DISABLED"));
        } finally {
            restoreSettings(adminToken, baseline);
        }
    }

    @Test
    void adminCanTightenPasswordPolicy() {
        String adminToken = login("admin@example.com", "Admin1234!");
        Map<String, Object> baseline = currentSettings(adminToken);

        try {
            Map<String, Object> tight = new HashMap<>(baseline);
            tight.put("passwordMinLength", 12);
            tight.put("passwordRequireDigit", true);

            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(ContentType.JSON)
                    .body(tight)
                    .when()
                    .patch("/api/settings")
                    .then()
                    .statusCode(200)
                    .body("passwordMinLength", equalTo(12));

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "email": "short-%s@example.com",
                              "password": "Secret123",
                              "firstName": "Corta",
                              "lastName": "Clave"
                            }
                            """.formatted(System.nanoTime()))
                    .when()
                    .post("/api/auth/register")
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("WEAK_PASSWORD"));
        } finally {
            restoreSettings(adminToken, baseline);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> currentSettings(String adminToken) {
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/settings")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);
    }

    private void restoreSettings(String adminToken, Map<String, Object> baseline) {
        Map<String, Object> body = new HashMap<>();
        body.put("allowPublicRegistration", baseline.get("allowPublicRegistration"));
        body.put("maxFailedAttempts", baseline.get("maxFailedAttempts"));
        body.put("lockDurationMinutes", baseline.get("lockDurationMinutes"));
        body.put("passwordMinLength", baseline.get("passwordMinLength"));
        body.put("passwordMaxLength", baseline.get("passwordMaxLength"));
        body.put("passwordRequireUppercase", baseline.get("passwordRequireUppercase"));
        body.put("passwordRequireLowercase", baseline.get("passwordRequireLowercase"));
        body.put("passwordRequireDigit", baseline.get("passwordRequireDigit"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .patch("/api/settings")
                .then()
                .statusCode(200);
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
