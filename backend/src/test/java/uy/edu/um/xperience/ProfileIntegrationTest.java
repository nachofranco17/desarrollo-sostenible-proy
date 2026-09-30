package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfileIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String NEW_PASSWORD = "otra frase de prueba segura";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @Test
    void talentCanReadOwnProfileOnly() throws Exception {
        var client = new Browser();
        String email = email();
        client.register(email);
        assertThat(client.get("/profile/me").statusCode()).isEqualTo(401);
        client.login(email, PASSWORD);
        var response = client.get("/profile/me");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("nombre").asText()).isEqualTo("Ana");
        assertThat(body.path("apellido").asText()).isEqualTo("Prueba");
        assertThat(body.path("correo").asText()).isEqualTo(email);
        assertThat(body.path("telefono").isNull()).isTrue();
        assertThat(body.path("especializaciones").isArray()).isTrue();
        assertThat(body.path("especializaciones")).isEmpty();
        assertThat(body.path("especializacionesDisponibles").isArray()).isTrue();
        assertThat(body.path("especializacionesDisponibles")).isNotEmpty();
        assertThat(body.path("notificarNovedadesCursos").asBoolean()).isTrue();
        assertThat(body.path("notificarOfertas").asBoolean()).isTrue();
        assertThat(body.path("progresoCursos").isArray()).isTrue();
        assertThat(body.path("proyectosCompletados").isArray()).isTrue();
        assertThat(body.path("logros").isArray()).isTrue();
        assertThat(response.body()).doesNotContain("password", "hash", "rol", "tipoCuenta");
    }

    @Test
    void talentCanUpdatePersonalContactSpecializationsAndNotificationsAndChangesPersist() throws Exception {
        var client = new Browser();
        String email = email();
        client.register(email);
        client.login(email, PASSWORD);
        String updatedEmail = "actualizado-" + email;
        ObjectNode payload = json.createObjectNode();
        payload.put("nombre", "Ana María");
        payload.put("apellido", "Pérez");
        payload.put("correo", updatedEmail);
        payload.put("telefono", "+598 99 123 456");
        payload.putArray("especializaciones").add("Backend").add("Seguridad");
        payload.put("notificarNovedadesCursos", false);
        payload.put("notificarOfertas", true);
        var update = client.patch("/profile/me", json.writeValueAsString(payload), true);
        assertThat(update.statusCode()).isEqualTo(200);
        JsonNode updated = json.readTree(update.body());
        assertThat(updated.path("nombre").asText()).isEqualTo("Ana María");
        assertThat(updated.path("apellido").asText()).isEqualTo("Pérez");
        assertThat(updated.path("correo").asText()).isEqualTo(updatedEmail);
        assertThat(updated.path("telefono").asText()).isEqualTo("+598 99 123 456");
        assertThat(updated.path("especializaciones").get(0).asText()).isEqualTo("Backend");
        assertThat(updated.path("especializaciones").get(1).asText()).isEqualTo("Seguridad");
        assertThat(updated.path("notificarNovedadesCursos").asBoolean()).isFalse();
        assertThat(updated.path("notificarOfertas").asBoolean()).isTrue();

        client.post("/auth/logout", "", "application/json", true);
        assertThat(client.login(updatedEmail, PASSWORD).statusCode()).isEqualTo(204);
        JsonNode reloaded = json.readTree(client.get("/profile/me").body());
        assertThat(reloaded.path("nombre").asText()).isEqualTo("Ana María");
        assertThat(reloaded.path("correo").asText()).isEqualTo(updatedEmail);
        assertThat(reloaded.path("telefono").asText()).isEqualTo("+598 99 123 456");
        assertThat(reloaded.path("especializaciones")).hasSize(2);
        assertThat(reloaded.path("notificarNovedadesCursos").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT telefono FROM perfil_talento WHERE usuario_id = ?",
            String.class, accounts.byEmail(updatedEmail).orElseThrow().id()))
            .isEqualTo("+598 99 123 456");
    }

    @Test
    void acceptsValidPhoneAndRejectsPhoneWithLettersWithoutSilentFix() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);

        ObjectNode valid = basePayload(email);
        valid.put("telefono", "099 123 456");
        assertThat(client.patch("/profile/me", json.writeValueAsString(valid), true).statusCode()).isEqualTo(200);
        assertThat(json.readTree(client.get("/profile/me").body()).path("telefono").asText()).isEqualTo("099 123 456");

        ObjectNode invalid = basePayload(email);
        invalid.put("telefono", "099abc123");
        assertThat(client.patch("/profile/me", json.writeValueAsString(invalid), true).statusCode()).isEqualTo(400);
        assertThat(json.readTree(client.get("/profile/me").body()).path("telefono").asText()).isEqualTo("099 123 456");
    }

    @Test
    void rejectsExcessivelyLongPhone() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("telefono", "1".repeat(31));
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
    }

    @Test
    void rejectsBlankAndExcessivelyLongNames() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);

        ObjectNode blank = basePayload(email);
        blank.put("nombre", "   ");
        assertThat(client.patch("/profile/me", json.writeValueAsString(blank), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");

        ObjectNode tooLong = basePayload(email);
        tooLong.put("apellido", "A".repeat(81));
        assertThat(client.patch("/profile/me", json.writeValueAsString(tooLong), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email).orElseThrow().apellido()).isEqualTo("Prueba");
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("correo", "no-es-un-correo");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email)).isPresent();
    }

    @Test
    void rejectsUnknownSpecialization() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.putArray("especializaciones").add("HackeoEspacial");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(json.readTree(client.get("/profile/me").body()).path("especializaciones")).isEmpty();
    }

    @Test
    void rejectsNonBooleanNotificationPreference() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("notificarNovedadesCursos", "true");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(json.readTree(client.get("/profile/me").body()).path("notificarNovedadesCursos").asBoolean()).isTrue();
    }

    @Test
    void rejectsObjectWhereStringExpectedAndArrayWhereScalarExpected() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);

        ObjectNode objectName = basePayload(email);
        objectName.set("nombre", json.createObjectNode().put("valor", "Ana"));
        assertThat(client.patch("/profile/me", json.writeValueAsString(objectName), true).statusCode()).isEqualTo(400);

        ObjectNode arrayPhone = basePayload(email);
        ArrayNode phones = arrayPhone.putArray("telefono");
        phones.add("099123456");
        assertThat(client.patch("/profile/me", json.writeValueAsString(arrayPhone), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void rejectsExcessivelyLongTextField() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("correo", "a".repeat(250) + "@example.test");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
    }

    @Test
    void omittedPatchFieldDoesNotChangeExistingValue() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode first = basePayload(email);
        first.put("telefono", "099111222");
        first.put("nombre", "Carla");
        assertThat(client.patch("/profile/me", json.writeValueAsString(first), true).statusCode()).isEqualTo(200);

        ObjectNode partial = json.createObjectNode();
        partial.put("apellido", "Gómez");
        assertThat(client.patch("/profile/me", json.writeValueAsString(partial), true).statusCode()).isEqualTo(200);
        JsonNode profile = json.readTree(client.get("/profile/me").body());
        assertThat(profile.path("nombre").asText()).isEqualTo("Carla");
        assertThat(profile.path("apellido").asText()).isEqualTo("Gómez");
        assertThat(profile.path("telefono").asText()).isEqualTo("099111222");
    }

    @Test
    void talentCanChangePasswordFromProfile() throws Exception {
        var client = new Browser();
        String email = email();
        client.register(email);
        client.login(email, PASSWORD);
        ObjectNode payload = basePayload(email);
        payload.put("passwordActual", PASSWORD);
        payload.put("passwordNueva", NEW_PASSWORD);
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(200);
        client.post("/auth/logout", "", "application/json", true);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(401);
        assertThat(client.login(email, NEW_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(passwords.matches(NEW_PASSWORD, accounts.byEmail(email).orElseThrow().passwordHash())).isTrue();
    }

    @Test
    void talentACannotModifyTalentBProfileViaBodyIdentifiers() throws Exception {
        var first = new Browser();
        var second = new Browser();
        String emailA = email();
        String emailB = email();
        first.register(emailA);
        second.register(emailB);
        first.login(emailA, PASSWORD);
        second.login(emailB, PASSWORD);
        UUID idB = accounts.byEmail(emailB).orElseThrow().id();

        ObjectNode attack = basePayload(emailA);
        attack.put("nombre", "Intruso");
        attack.put("userId", idB.toString());
        attack.put("talentId", idB.toString());
        attack.put("profileId", idB.toString());
        assertThat(first.patch("/profile/me", json.writeValueAsString(attack), true).statusCode()).isEqualTo(400);

        ObjectNode legitimate = basePayload(emailA);
        legitimate.put("nombre", "SoloA");
        assertThat(first.patch("/profile/me", json.writeValueAsString(legitimate), true).statusCode()).isEqualTo(200);
        assertThat(json.readTree(second.get("/profile/me").body()).path("nombre").asText()).isEqualTo("Ana");
        assertThat(accounts.byEmail(emailB).orElseThrow().nombre()).isEqualTo("Ana");
        assertThat(accounts.byEmail(emailA).orElseThrow().nombre()).isEqualTo("SoloA");
    }

    @Test
    void profileTargetCannotBeChangedByUrlUserId() throws Exception {
        var client = new Browser();
        String email = email();
        client.register(email);
        client.login(email, PASSWORD);
        UUID other = accounts.byEmail(email).orElseThrow().id();
        assertThat(client.get("/profile/" + other).statusCode()).isEqualTo(403);
        assertThat(client.patch("/profile/" + other, json.writeValueAsString(basePayload(email)), true).statusCode())
            .isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rol", "role", "permissions", "isAdmin", "progress", "achievements",
        "progresoCursos", "proyectosCompletados", "logros", "tipoCuenta", "activo", "id", "usuarioId", "userId"})
    void rejectsUnauthorizedWriteFieldsCompletely(String field) throws Exception {
        var client = new Browser();
        String email = email();
        client.register(email);
        client.login(email, PASSWORD);
        ObjectNode payload = basePayload(email);
        payload.put("nombre", "NoDebeGuardarse");
        payload.put(field, "ADMIN");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
        assertThat(json.readTree(client.get("/profile/me").body()).path("nombre").asText()).isEqualTo("Ana");
    }

    @Test
    void staffCannotAccessTalentProfileEndpoint() throws Exception {
        String email = email();
        provisioner.create("Empresa perfil", new RegisterRequest(email, "Admin", "Prueba", PASSWORD), "test-suite");
        var client = new Browser();
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(client.get("/profile/me").statusCode()).isEqualTo(403);
        assertThat(client.patch("/profile/me", json.writeValueAsString(basePayload(email)), true).statusCode())
            .isEqualTo(403);
    }

    @Test
    void profileWritesRequireAuthenticationAndCsrf() throws Exception {
        var client = new Browser();
        String email = email();
        client.register(email);
        ObjectNode payload = basePayload(email);
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(401);
        client.login(email, PASSWORD);
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), false).statusCode()).isEqualTo(403);
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsTooManySpecializations() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        ArrayNode specs = payload.putArray("especializaciones");
        for (JsonNode option : json.readTree(client.get("/profile/me").body()).path("especializacionesDisponibles")) {
            specs.add(option.asText());
        }
        specs.add("Backend");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(json.readTree(client.get("/profile/me").body()).path("especializaciones")).isEmpty();
    }

    @Test
    void rejectsDuplicateSpecializations() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.putArray("especializaciones").add("Backend").add("Backend");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(json.readTree(client.get("/profile/me").body()).path("especializaciones")).isEmpty();
    }

    @Test
    void rejectsNestedOverpostingObjectsCompletely() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("nombre", "NoDebeGuardarse");
        payload.set("user", json.createObjectNode().put("role", "ADMIN"));
        payload.set("account", json.createObjectNode().put("permissions", "ALL"));
        payload.set("profile", json.createObjectNode().put("userId", UUID.randomUUID().toString()));
        payload.set("settings", json.createObjectNode().put("isAdmin", true));
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void rejectsIncorrectContentType() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        var response = client.patchWithContentType("/profile/me",
            json.writeValueAsString(basePayload(email)), "text/plain", true);
        assertThat(response.statusCode()).isIn(415, 400);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void rejectsExcessivelyLargeBody() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("nombre", "A".repeat(20_000));
        int status;
        try {
            status = client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode();
        } catch (IOException | InterruptedException ex) {
            status = 413;
        }
        assertThat(status).isIn(400, 413);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void rejectsMixedValidAndInvalidWithoutPersistingAnyChange() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("nombre", "Carolina");
        payload.put("telefono", "099abc123");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        JsonNode profile = json.readTree(client.get("/profile/me").body());
        assertThat(profile.path("nombre").asText()).isEqualTo("Ana");
        assertThat(profile.path("telefono").isNull()).isTrue();
    }

    @Test
    void rejectsReadonlyDerivedFieldsViaManualRequest() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("nombre", "NoDebeGuardarse");
        payload.putArray("progresoCursos").addObject().put("nombre", "Curso falso").put("porcentajeAvance", 100);
        payload.putArray("logros").addObject().put("titulo", "Logro falso");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void acceptsLegitimateUnicodeNames() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);
        ObjectNode payload = basePayload(email);
        payload.put("nombre", "José María");
        payload.put("apellido", "Ñúñez");
        assertThat(client.patch("/profile/me", json.writeValueAsString(payload), true).statusCode()).isEqualTo(200);
        JsonNode profile = json.readTree(client.get("/profile/me").body());
        assertThat(profile.path("nombre").asText()).isEqualTo("José María");
        assertThat(profile.path("apellido").asText()).isEqualTo("Ñúñez");
    }

    @Test
    void rejectsControlAndInvisibleCharactersInNames() throws Exception {
        var client = loggedClient();
        String email = currentEmail(client);

        ObjectNode withNull = basePayload(email);
        withNull.put("nombre", "Ana\u0000");
        assertThat(client.patch("/profile/me", json.writeValueAsString(withNull), true).statusCode()).isEqualTo(400);

        ObjectNode withZeroWidth = basePayload(email);
        withZeroWidth.put("apellido", "Prueba\u200B");
        assertThat(client.patch("/profile/me", json.writeValueAsString(withZeroWidth), true).statusCode()).isEqualTo(400);

        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
        assertThat(accounts.byEmail(email).orElseThrow().apellido()).isEqualTo("Prueba");
    }

    @Test
    void emailUniquenessConflictDoesNotLeakInternalDetails() throws Exception {
        var first = new Browser();
        var second = new Browser();
        String emailA = email();
        String emailB = email();
        first.register(emailA);
        second.register(emailB);
        first.login(emailA, PASSWORD);
        second.login(emailB, PASSWORD);

        ObjectNode payload = basePayload(emailB);
        payload.put("correo", emailA);
        var response = second.patch("/profile/me", json.writeValueAsString(payload), true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("SQL", "constraint", "usuario", "Duplicate", "jdbc", emailA);
        assertThat(json.readTree(response.body()).path("message").asText())
            .isEqualTo("No se pudo guardar el perfil con esos datos.");
        assertThat(accounts.byEmail(emailB).orElseThrow().correo()).isEqualTo(emailB);
        assertThat(accounts.byEmail(emailA)).isPresent();
    }

    private Browser loggedClient() throws Exception {
        var client = new Browser();
        String address = email();
        client.register(address);
        client.login(address, PASSWORD);
        return client;
    }

    private String currentEmail(Browser client) throws Exception {
        return json.readTree(client.get("/account").body()).path("correo").asText();
    }

    private ObjectNode basePayload(String email) {
        ObjectNode payload = json.createObjectNode();
        payload.put("nombre", "Ana");
        payload.put("apellido", "Prueba");
        payload.put("correo", email);
        payload.putNull("telefono");
        payload.putArray("especializaciones");
        payload.put("notificarNovedadesCursos", true);
        payload.put("notificarOfertas", true);
        return payload;
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, String body, String contentType, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType);
            if (csrf) {
                var token = json.readTree(get("/auth/csrf").body());
                request.header(token.path("headerName").asText(), token.path("token").asText());
            }
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> patch(String path, String body, boolean csrf) throws Exception {
            return patchWithContentType(path, body, "application/json", csrf);
        }
        HttpResponse<String> patchWithContentType(String path, String body, String contentType, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType);
            if (csrf) {
                var token = json.readTree(get("/auth/csrf").body());
                request.header(token.path("headerName").asText(), token.path("token").asText());
            }
            return client.send(request.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> register(String email) throws Exception {
            return post("/auth/register", json.writeValueAsString(new RegisterRequest(email, "Ana", "Prueba", PASSWORD)),
                "application/json", true);
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            return post("/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", true);
        }
    }
}
