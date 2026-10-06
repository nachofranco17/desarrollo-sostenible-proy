package uy.edu.um.xperience;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.Denegaciones;
import uy.edu.um.xperience.security.RegistroAccesosEstructurado;
import static org.assertj.core.api.Assertions.*;

/**
 * RS18 — Registro estructurado de intentos de acceso denegados.
 * Captura el logger de seguridad y comprueba metadatos mínimos y ausencia de secretos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Rs18DeniedAccessLogTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String SECRET_PASSWORD = "SuperSecretoQueNoDebeLoguearse99";
    private static final String COURSES = "/company/courses";
    private static final byte[] PDF = ("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    private ListAppender<ILoggingEvent> appender;
    private Logger securityLogger;

    @BeforeEach
    void captureSecurityLog() {
        securityLogger = (Logger) LoggerFactory.getLogger(RegistroAccesosEstructurado.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        securityLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        if (securityLogger != null && appender != null) {
            securityLogger.detachAppender(appender);
        }
    }

    @Test
    void rs11HorizontalDenialIsLoggedOnceWithStructuredMetadata() throws Exception {
        var talentA = registerTalent();
        var talentB = registerTalent();
        clearEvents();

        var response = talentA.get("/profile/" + talentB.id);
        assertThat(response.statusCode()).isEqualTo(403);

        JsonNode event = singleEvent();
        assertDenialBasics(event, talentA.id.toString(), "profile", "read", Denegaciones.INSTANCE_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(talentB.id.toString());
        assertNoSensitiveData(event, talentA.email, talentB.email, PASSWORD, SECRET_PASSWORD);
    }

    @Test
    void rs9VerticalDenialIsLoggedWhenRoleLacksAction() throws Exception {
        UUID company = company();
        var editor = member(company, "EDITOR");
        UUID courseId = create(editor, "CURSO", "Privado");
        var recruiter = member(company, "RECLUTADOR");
        clearEvents();

        var response = recruiter.get(COURSES + "/" + courseId);
        assertThat(response.statusCode()).isEqualTo(403);

        JsonNode event = singleEvent();
        assertDenialBasics(event, recruiter.id.toString(), "course", "read", Denegaciones.ROLE_PERMISSION_DENIED);
        assertThat(event.path("action").asText()).isEqualTo("curso.ver_borrador");
        assertNoSensitiveData(event, recruiter.email, PASSWORD, SECRET_PASSWORD);
    }

    @Test
    void rs15CrossCompanyDenialIsLogged() throws Exception {
        UUID companyA = company();
        UUID companyB = company();
        var editorA = member(companyA, "EDITOR");
        var editorB = member(companyB, "EDITOR");
        UUID courseId = create(editorA, "CURSO", "De A");
        clearEvents();

        var response = editorB.get(COURSES + "/" + courseId);
        assertThat(response.statusCode()).isEqualTo(403);

        JsonNode event = singleEvent();
        assertDenialBasics(event, editorB.id.toString(), "course", "read", Denegaciones.ORGANIZATION_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(courseId.toString());
        assertNoSensitiveData(event, editorA.email, editorB.email, PASSWORD);
    }

    @Test
    void rs12ForbiddenFieldDenialLogsFieldNameButNeverItsValue() throws Exception {
        var talent = registerTalent();
        clearEvents();

        ObjectNode attack = json.createObjectNode();
        attack.put("nombre", "NoDebeGuardarse");
        attack.put("rol", "ADMIN");
        attack.put("password", SECRET_PASSWORD);
        attack.put("passwordNueva", SECRET_PASSWORD);
        attack.put("correo", "filtrar-" + talent.email);
        attack.put("telefono", "099999999");

        var response = talent.patch("/profile/me", json.writeValueAsString(attack), true);
        assertThat(response.statusCode()).isEqualTo(400);

        JsonNode event = singleEvent();
        assertDenialBasics(event, talent.id.toString(), "profile", "update", Denegaciones.FIELD_ACCESS_DENIED);
        assertThat(event.path("field").asText()).isEqualTo("rol");
        assertThat(event.has("value")).isFalse();
        String raw = event.toString();
        assertThat(raw).doesNotContain(SECRET_PASSWORD);
        assertThat(raw).doesNotContain("ADMIN");
        assertThat(raw).doesNotContain("099999999");
        assertThat(raw).doesNotContain(talent.email);
        assertThat(raw).doesNotContain("NoDebeGuardarse");
        assertThat(raw).doesNotContain("\"password\"");
        assertThat(raw).doesNotContain("Authorization");
        assertThat(raw).doesNotContain("Cookie");
    }

    @Test
    void rs14UnauthorizedFileDownloadIsLoggedWithoutContentOrSignedUrl() throws Exception {
        UUID companyA = company();
        UUID companyB = company();
        var editorA = member(companyA, "EDITOR");
        var editorB = member(companyB, "EDITOR");
        UUID courseId = create(editorA, "CURSO", "Con archivo");
        String materialId = json.readTree(editorA.upload(COURSES + "/" + courseId + "/materials", "secreto.pdf", PDF).body())
            .path("materiales").get(0).path("id").asText();
        clearEvents();

        var response = editorB.getBytes(COURSES + "/" + courseId + "/materials/" + materialId);
        assertThat(response.statusCode()).isEqualTo(403);

        JsonNode event = singleEvent();
        assertDenialBasics(event, editorB.id.toString(), "file", "download", Denegaciones.FILE_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(materialId);
        String raw = event.toString();
        assertThat(raw).doesNotContain("%PDF");
        assertThat(raw).doesNotContain("secreto.pdf");
        assertThat(raw).doesNotContain("X-Amz-");
        assertThat(raw).doesNotContain("Signature=");
        assertThat(raw).doesNotContain("claveAlmacen");
        assertThat(raw).doesNotContain("./.data");
        assertThat(raw).doesNotContain("Bearer ");
        assertThat(raw).doesNotContain(PASSWORD);
        assertNoSensitiveData(event, editorA.email, editorB.email);
    }

    @Test
    void rf6CrossCompanyListProfileCurriculumAndPublishDenialsHaveSafeMetadata() throws Exception {
        UUID companyA = company();
        var adminA = member(companyA, "ADMIN");
        var recruiterB = member(company(), "RECLUTADOR");
        var talent = registerTalent();
        assertThat(talent.upload("/profile/me/curriculum", "privado.pdf", PDF).statusCode()).isEqualTo(201);
        var created = adminA.send("POST", "/company/offers", json.writeValueAsString(Map.of(
            "titulo", "Oferta privada de empresa", "descripcion", "Descripción", "cursosRequeridos", List.of())));
        assertThat(created.statusCode()).isEqualTo(201);
        String offerId = json.readTree(created.body()).path("id").asText();
        assertThat(adminA.send("POST", "/company/offers/" + offerId + "/publish", "{}").statusCode())
            .isIn(200, 204);
        var applied = talent.send("POST", "/offers/" + offerId + "/applications", "{}");
        assertThat(applied.statusCode()).isEqualTo(201);
        String applicationId = json.readTree(applied.body()).path("id").asText();
        String applications = "/company/offers/" + offerId + "/applications";

        clearEvents();
        assertThat(recruiterB.get(applications + "?q=busqueda-secreta").statusCode()).isEqualTo(403);
        JsonNode event = singleEvent();
        assertDenialBasics(event, recruiterB.id.toString(), "application", "list", Denegaciones.ORGANIZATION_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(offerId);
        assertNoSensitiveData(event, adminA.email, recruiterB.email, talent.email, "busqueda-secreta");

        clearEvents();
        assertThat(recruiterB.get(applications + "/" + applicationId + "/profile").statusCode()).isEqualTo(403);
        event = singleEvent();
        assertDenialBasics(event, recruiterB.id.toString(), "profile", "read", Denegaciones.ORGANIZATION_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(applicationId);
        assertNoSensitiveData(event, adminA.email, talent.email, PASSWORD);

        clearEvents();
        assertThat(recruiterB.getBytes(applications + "/" + applicationId + "/curriculum").statusCode()).isEqualTo(403);
        event = singleEvent();
        assertDenialBasics(event, recruiterB.id.toString(), "file", "download", Denegaciones.FILE_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(applicationId);
        assertNoSensitiveData(event, "privado.pdf", "%PDF", "claveAlmacen", "./.data", talent.email);

        clearEvents();
        assertThat(recruiterB.send("POST", "/company/offers/" + offerId + "/publish", "{}").statusCode()).isEqualTo(403);
        event = singleEvent();
        assertDenialBasics(event, recruiterB.id.toString(), "offer", "publish", Denegaciones.ORGANIZATION_ACCESS_DENIED);
        assertThat(event.path("resourceId").asText()).isEqualTo(offerId);
        assertNoSensitiveData(event, adminA.email, recruiterB.email, talent.email, PASSWORD);
    }

    // ---- assertions ----

    private void assertDenialBasics(JsonNode event, String userId, String resourceType, String operation, String reason) {
        assertThat(event.path("event").asText()).isEqualTo("authorization_denied");
        assertThat(event.path("result").asText()).isEqualTo("denied");
        assertThat(event.path("userId").asText()).isEqualTo(userId);
        assertThat(event.path("resourceType").asText()).isEqualTo(resourceType);
        assertThat(event.path("operation").asText()).isEqualTo(operation);
        assertThat(event.path("reason").asText()).isEqualTo(reason);
        assertThat(event.path("origin").asText()).isNotBlank();
        assertThat(event.path("timestamp").asText()).isNotBlank();
        assertThatCode(() -> Instant.parse(event.path("timestamp").asText())).doesNotThrowAnyException();
        assertThat(event.isObject()).isTrue();
    }

    private void assertNoSensitiveData(JsonNode event, String... secrets) {
        String raw = event.toString();
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                assertThat(raw).doesNotContain(secret);
            }
        }
        assertThat(raw).doesNotContain("\"password\"");
        assertThat(raw).doesNotContain("Authorization");
        assertThat(raw).doesNotContain("Cookie");
        assertThat(raw).doesNotContain("Set-Cookie");
        assertThat(event.has("body")).isFalse();
        assertThat(event.has("headers")).isFalse();
        assertThat(event.has("token")).isFalse();
        assertThat(event.has("value")).isFalse();
    }

    private JsonNode singleEvent() throws Exception {
        assertThat(appender.list).as("un evento RS18 por denegación").hasSize(1);
        return json.readTree(appender.list.get(0).getFormattedMessage());
    }

    private void clearEvents() {
        appender.list.clear();
    }

    // ---- apoyo ----

    private Browser registerTalent() throws Exception {
        String email = email();
        var client = new Browser(email);
        client.register(email);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
    }

    private UUID company() {
        return provisioner.create("Empresa " + UUID.randomUUID(),
            new RegisterRequest(email(), "Admin", "Prueba", PASSWORD), "test-suite");
    }

    private Browser member(UUID company, String role) throws Exception {
        String email = email();
        UUID id = accounts.create(email, "Staff", "Prueba", passwords.encode(PASSWORD), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')",
            id, company, role);
        var client = new Browser(email);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        client.id = id;
        return client;
    }

    private UUID create(Browser client, String type, String title) throws Exception {
        var response = client.send("POST", COURSES, course(type, title));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private String course(String type, String title) throws Exception {
        return json.writeValueAsString(Map.of(
            "tipo", type, "titulo", title, "descripcion", "Descripción", "tecnologia", "Java",
            "nivel", "INICIAL", "duracionHoras", 10, "costo", 0));
    }

    private static String email() {
        return UUID.randomUUID() + "@example.test";
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api" + path);
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String email;
        UUID id;

        Browser(String email) {
            this.email = email;
        }

        HttpResponse<String> register(String mail) throws Exception {
            return post("/auth/register", json.writeValueAsString(
                new RegisterRequest(mail, "Ana", "Prueba", PASSWORD)), "application/json", true);
        }

        HttpResponse<String> login(String mail, String password) throws Exception {
            return post("/auth/login",
                "correo=" + URLEncoder.encode(mail, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", true);
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<byte[]> getBytes(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<String> patch(String path, String body, boolean csrf) throws Exception {
            return post(path, body, "application/json", csrf, "PATCH");
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return post(path, body, "application/json", true, method);
        }

        HttpResponse<String> upload(String path, String filename, byte[] content) throws Exception {
            String boundary = "----rs18" + UUID.randomUUID();
            byte[] body = multipart(boundary, filename, content);
            var builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            var token = json.readTree(get("/auth/csrf").body());
            builder.header(token.path("headerName").asText(), token.path("token").asText());
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> post(String path, String body, String contentType, boolean csrf) throws Exception {
            return post(path, body, contentType, csrf, "POST");
        }

        private HttpResponse<String> post(String path, String body, String contentType, boolean csrf, String method)
                throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            if (csrf) {
                var token = json.readTree(get("/auth/csrf").body());
                builder.header(token.path("headerName").asText(), token.path("token").asText());
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private static byte[] multipart(String boundary, String filename, byte[] content) {
            String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"archivo\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
            String suffix = "\r\n--" + boundary + "--\r\n";
            byte[] head = prefix.getBytes(StandardCharsets.UTF_8);
            byte[] tail = suffix.getBytes(StandardCharsets.UTF_8);
            byte[] all = new byte[head.length + content.length + tail.length];
            System.arraycopy(head, 0, all, 0, head.length);
            System.arraycopy(content, 0, all, head.length, content.length);
            System.arraycopy(tail, 0, all, head.length + content.length, tail.length);
            return all;
        }
    }
}
