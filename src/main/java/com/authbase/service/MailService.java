package com.authbase.service;

import com.authbase.config.AuthProperties;
import com.authbase.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class);
    private static final Pattern FROM_PATTERN = Pattern.compile("^(?:(.+?)\\s*)?<([^>]+)>$|^([^<>\\s]+@[^<>\\s]+)$");
    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final Mailer mailer;
    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final String from;
    private final Optional<String> brevoApiKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public MailService(
            Mailer mailer,
            AuthProperties properties,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "quarkus.mailer.from", defaultValue = "Auth Base <noreply@localhost>") String from,
            @ConfigProperty(name = "brevo.api-key") Optional<String> brevoApiKey
    ) {
        this.mailer = mailer;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.from = from;
        this.brevoApiKey = brevoApiKey.filter(value -> !value.isBlank());
    }

    public void sendPasswordReset(String toEmail, String rawToken) {
        String base = properties.frontendUrl().replaceAll("/$", "");
        String link = base + "/reset-password?token=" + rawToken;
        String subject = "Restablecer contraseña — Auth Base";
        String body = """
                Hola,

                Recibimos un pedido para restablecer tu contraseña.
                Abrí este enlace (válido por un tiempo limitado):

                %s

                Si no pediste este cambio, ignorá este correo.
                """.formatted(link);

        try {
            if (brevoApiKey.isPresent()) {
                sendViaBrevoApi(toEmail, subject, body);
            } else {
                mailer.send(Mail.withText(toEmail, subject, body).setFrom(from));
            }
            LOG.infof("Mail de restablecimiento enviado a %s", toEmail);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "No se pudo enviar el mail de restablecimiento a %s", toEmail);
            throw ApiException.serviceUnavailable(
                    "MAIL_SEND_FAILED",
                    "No se pudo enviar el correo. Probá de nuevo en unos minutos."
            );
        }
    }

    private void sendViaBrevoApi(String toEmail, String subject, String textBody) throws Exception {
        SenderParts sender = parseFrom(from);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("name", sender.name(), "email", sender.email()));
        payload.put("to", List.of(Map.of("email", toEmail)));
        payload.put("subject", subject);
        payload.put("textContent", textBody);

        String json = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_SEND_URL))
                .timeout(Duration.ofSeconds(30))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("api-key", brevoApiKey.orElseThrow())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.errorf("Brevo API respondió %s: %s", response.statusCode(), response.body());
            throw ApiException.serviceUnavailable(
                    "MAIL_SEND_FAILED",
                    "No se pudo enviar el correo. Probá de nuevo en unos minutos."
            );
        }
        LOG.debugf("Brevo API OK (%s): %s", response.statusCode(), response.body());
    }

    static SenderParts parseFrom(String raw) {
        Matcher matcher = FROM_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return new SenderParts("Auth Base", raw.trim());
        }
        if (matcher.group(3) != null) {
            return new SenderParts("Auth Base", matcher.group(3).trim());
        }
        String name = matcher.group(1) != null ? matcher.group(1).trim() : "Auth Base";
        String email = matcher.group(2).trim();
        if (name.isEmpty()) {
            name = "Auth Base";
        }
        return new SenderParts(name, email);
    }

    record SenderParts(String name, String email) {
    }
}
