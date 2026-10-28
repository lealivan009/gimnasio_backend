package com.authbase;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class UserResourceTest {

    @Test
    void anonymousCannotListUsers() {
        given()
                .when()
                .get("/api/users")
                .then()
                .statusCode(401);
    }

    @Test
    void userCannotListUsers() {
        String email = "member-" + System.nanoTime() + "@example.com";
        String token = register(email, "Usuario Demo");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/users")
                .then()
                .statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    void adminCanManageUsers() {
        String adminToken = login("admin@example.com", "Admin1234!");
        String email = "managed-" + System.nanoTime() + "@example.com";

        String userId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123",
                          "firstName": "Usuario",
                          "lastName": "Gestionado",
                          "role": "USER"
                        }
                        """.formatted(email))
                .when()
                .post("/api/users")
                .then()
                .statusCode(200)
                .body("email", equalTo(email))
                .body("role", equalTo("USER"))
                .body("enabled", equalTo(true))
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/users")
                .then()
                .statusCode(200)
                .body("items.email", hasItem(email));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .queryParam("q", email)
                .when()
                .get("/api/users")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].email", equalTo(email));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/users/" + userId)
                .then()
                .statusCode(200)
                .body("id", equalTo(userId))
                .body("email", equalTo(email))
                .body("firstName", equalTo("Usuario"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "firstName": "Usuario",
                          "lastName": "Editado",
                          "phone": "111222333",
                          "city": "Córdoba"
                        }
                        """)
                .when()
                .patch("/api/users/" + userId)
                .then()
                .statusCode(200)
                .body("lastName", equalTo("Editado"))
                .body("phone", equalTo("111222333"))
                .body("city", equalTo("Córdoba"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "role": "ADMIN",
                          "enabled": false
                        }
                        """)
                .when()
                .patch("/api/users/" + userId)
                .then()
                .statusCode(200)
                .body("role", equalTo("ADMIN"))
                .body("enabled", equalTo(false))
                .body("phone", equalTo("111222333"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/users/" + userId)
                .then()
                .statusCode(200)
                .body("message", equalTo("Usuario eliminado"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/users/" + userId)
                .then()
                .statusCode(404);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/users")
                .then()
                .statusCode(200)
                .body("items.email", not(hasItem(email)));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123"
                        }
                        """.formatted(email))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void adminCannotDeleteSelf() {
        String adminToken = login("admin@example.com", "Admin1234!");
        String adminId = given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/users/" + adminId)
                .then()
                .statusCode(400)
                .body("error", equalTo("CANNOT_DELETE_SELF"));
    }

    private String register(String email, String firstName) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123",
                          "firstName": "%s",
                          "lastName": "Demo"
                        }
                        """.formatted(email, firstName))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
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
