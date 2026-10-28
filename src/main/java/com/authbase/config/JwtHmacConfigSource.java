package com.authbase.config;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Publica el secreto HMAC como JWK para que SmallRye JWT no use el par RSA de Dev Mode.
 */
public class JwtHmacConfigSource implements ConfigSource {

    static final String DEFAULT_SECRET = "local-dev-only-secret-key-32-chars-min";

    private final Map<String, String> values;

    public JwtHmacConfigSource() {
        String secret = resolveSecret();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        String jwk = "{\"kty\":\"oct\",\"kid\":\"auth-hmac\",\"k\":\"" + encoded + "\",\"alg\":\"HS256\"}";
        values = Map.of(
                "mp.jwt.verify.publickey", jwk,
                "smallrye.jwt.verify.algorithm", "HS256"
        );
    }

    @Override
    public Map<String, String> getProperties() {
        return values;
    }

    @Override
    public Set<String> getPropertyNames() {
        return values.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return values.get(propertyName);
    }

    @Override
    public String getName() {
        return "jwt-hmac-jwk";
    }

    @Override
    public int getOrdinal() {
        return 800;
    }

    private static String resolveSecret() {
        String fromEnv = System.getenv("JWT_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = readDotEnv(Path.of(".env"));
        if (fromFile == null) {
            fromFile = readDotEnv(Path.of("..", ".env"));
        }
        return fromFile != null ? fromFile : DEFAULT_SECRET;
    }

    private static String readDotEnv(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("JWT_SECRET=")) {
                    String value = trimmed.substring("JWT_SECRET=".length()).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value.isBlank() ? null : value;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }
}
