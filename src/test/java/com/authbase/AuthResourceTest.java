package com.authbase;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {

    @Test
    void registerLoginAndMe() {
        String email = "user-" + System.nanoTime() + "@example.com";

        String accessToken = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123",
                          "firstName": "Usuario",
                          "lastName": "Demo"
                        }
                        """.formatted(email))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("user.email", equalTo(email))
                .extract()
                .path("accessToken");

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
                .statusCode(200)
                .body("user.firstName", equalTo("Usuario"))
                .body("user.lastName", equalTo("Demo"));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo(email));
    }

    @Test
    void registerAndUpdateProfile() {
        String email = "profile-" + System.nanoTime() + "@example.com";

        String accessToken = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123",
                          "firstName": "Ana",
                          "lastName": "Perez",
                          "documentNumber": "30111222",
                          "phone": "1144445555",
                          "birthDate": "1994-05-20",
                          "street": "Av. Corrientes 1234",
                          "city": "CABA",
                          "province": "Buenos Aires",
                          "postalCode": "1043"
                        }
                        """.formatted(email))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(200)
                .body("user.phone", equalTo("1144445555"))
                .body("user.city", equalTo("CABA"))
                .extract()
                .path("accessToken");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "firstName": "Ana",
                          "lastName": "Perez",
                          "documentNumber": "30111222",
                          "phone": "1199990000",
                          "birthDate": "1994-05-20",
                          "street": "Calle Falsa 123",
                          "city": "Rosario",
                          "province": "Santa Fe",
                          "postalCode": "2000"
                        }
                        """)
                .when()
                .patch("/api/auth/me")
                .then()
                .statusCode(200)
                .body("phone", equalTo("1199990000"))
                .body("street", equalTo("Calle Falsa 123"))
                .body("city", equalTo("Rosario"));
    }

    @Test
    void loginRejectsBadPassword() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "nobody@example.com",
                          "password": "Wrong123"
                        }
                        """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("error", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void registerRejectsWeakPassword() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "weak@example.com",
                          "password": "password",
                          "firstName": "Usuario",
                          "lastName": "Demo"
                        }
                        """)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body("error", equalTo("WEAK_PASSWORD"));
    }

    @Test
    void forgotPasswordAlwaysReturnsGenericMessage() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "missing-%s@example.com"
                        }
                        """.formatted(System.nanoTime()))
                .when()
                .post("/api/auth/forgot-password")
                .then()
                .statusCode(200)
                .body("message", equalTo("Si el email está registrado, te enviamos un enlace para restablecer la contraseña."));
    }

    @Test
    void resetPasswordRejectsInvalidToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "token": "0123456789abcdef0123456789abcdef",
                          "newPassword": "Secret123"
                        }
                        """)
                .when()
                .post("/api/auth/reset-password")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_RESET_TOKEN"));
    }
}
