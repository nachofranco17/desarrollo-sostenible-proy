package uy.edu.um.xperience;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.course.FileStorage;
import uy.edu.um.xperience.curriculum.CurriculumRepository;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.Denegaciones;
import uy.edu.um.xperience.security.RegistroAccesosEstructurado;
import static org.assertj.core.api.Assertions.assertThat;

/** CV privado RF6: servidor real, sesión, CSRF, versiones y entradas estrictas. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurriculumIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String BASE = "/profile/me/curriculum";
    private static final byte[] PDF = "%PDF-1.4\n% Curriculum original\n%%EOF\n"
        .getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CurriculumRepository curriculums;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired FileStorage storage;
    @Autowired JdbcTemplate jdbc;

    @Test
    void uploadsPdfDetectedBySignatureWithSafeMetadataAndPrivateDownload() throws Exception {
        var talent = talent();
        var response = talent.upload(BASE, "../../curriculum.pdf", PDF, true);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("nombreOriginal").asText()).isEqualTo("curriculum.pdf");
        assertThat(body.path("tipoMime").asText()).isEqualTo("application/pdf");
        assertThat(body.path("tamanioBytes").asLong()).isEqualTo(PDF.length);
        assertThat(body.path("creadoEn").asText()).isNotBlank();
        assertThat(body.size()).isEqualTo(5);
        assertThat(response.body()).doesNotContain("usuarioId", "claveAlmacen", "almacen", "target", "ruta");
        UUID id = UUID.fromString(body.path("id").asText());
        assertThat(curriculums.current(talent.id).orElseThrow().id()).isEqualTo(id);

        var download = talent.getBytes(BASE);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(PDF);
        assertThat(download.headers().firstValue("Content-Type")).contains("application/pdf");
        assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
        assertThat(download.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(download.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }

    @Test
    void replacementKeepsThePreviousVersionAndChangesOnlyOwnCurrentReference() throws Exception {
        var talent = talent();
        UUID previousId = uploaded(talent, PDF);
        var previous = curriculums.find(previousId, talent.id).orElseThrow();
        byte[] replacement = "%PDF-1.7\n% Curriculum actualizado\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        UUID nextId = uploaded(talent, replacement);

        assertThat(nextId).isNotEqualTo(previousId);
        assertThat(curriculums.current(talent.id).orElseThrow().id()).isEqualTo(nextId);
        assertThat(curriculums.find(previousId, talent.id)).contains(previous);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curriculum WHERE usuario_id = ?",
            Integer.class, talent.id)).isEqualTo(2);
        assertThat(talent.getBytes(BASE).body()).isEqualTo(replacement);
        try (var oldFile = storage.open(previous.claveAlmacen()).getInputStream()) {
            assertThat(oldFile.readAllBytes()).isEqualTo(PDF);
        }
    }

    @Test
    void talentsDownloadOnlyTheirOwnCurrentCurriculum() throws Exception {
        var first = talent();
        var second = talent();
        UUID firstId = uploaded(first, PDF);
        byte[] secondPdf = "%PDF-1.4\n% Solo segundo\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        UUID secondId = uploaded(second, secondPdf);

        assertThat(first.getBytes(BASE).body()).isEqualTo(PDF);
        assertThat(second.getBytes(BASE).body()).isEqualTo(secondPdf);
        assertThat(curriculums.find(secondId, first.id)).isEmpty();
        assertThat(curriculums.find(firstId, second.id)).isEmpty();
        assertThat(first.get(BASE + "?usuarioId=" + second.id).statusCode()).isEqualTo(400);
        assertThat(curriculums.current(first.id).orElseThrow().id()).isEqualTo(firstId);
    }

    @Test
    void missingCurrentCurriculumIsReportedOnlyToItsOwner() throws Exception {
        var talent = talent();
        var response = talent.get(BASE);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("clave", "SQL", "target");
    }

    @Test
    void rejectsWrongSignatureEvenWithPdfExtensionAndContentType() throws Exception {
        var talent = talent();
        UUID original = uploaded(talent, PDF);
        var response = talent.multipart(BASE, List.of(new Part("archivo", "fake.pdf",
            "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), "application/pdf")), true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(curriculums.current(talent.id).orElseThrow().id()).isEqualTo(original);
        assertThat(count(talent.id)).isEqualTo(1);
    }

    @Test
    void acceptsExactlyFiveMiBAndRejectsLargerOrEmptyFilesWithoutReplacingCurrent() throws Exception {
        var talent = talent();
        byte[] maximum = Arrays.copyOf(PDF, MAX_BYTES);
        UUID current = uploaded(talent, maximum);
        assertThat(curriculums.current(talent.id).orElseThrow().tamanioBytes()).isEqualTo(MAX_BYTES);
        assertThat(talent.upload(BASE, "grande.pdf", Arrays.copyOf(PDF, MAX_BYTES + 1), true).statusCode())
            .isEqualTo(400);
        assertThat(talent.upload(BASE, "vacio.pdf", new byte[0], true).statusCode()).isEqualTo(400);
        assertThat(curriculums.current(talent.id).orElseThrow().id()).isEqualTo(current);
        assertThat(count(talent.id)).isEqualTo(1);
    }

    @Test
    void normalizesInvisibleAndUnsafeFilenameCharactersAndBoundsLength() throws Exception {
        var talent = talent();
        var response = talent.upload(BASE, "../../CV\u200B?;.exe", PDF, true);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json.readTree(response.body()).path("nombreOriginal").asText()).isEqualTo("CV.exe.pdf");
        var longName = talent.upload(BASE, "a".repeat(400) + ".pdf", PDF, true);
        assertThat(longName.statusCode()).isEqualTo(201);
        assertThat(json.readTree(longName.body()).path("nombreOriginal").asText()).hasSize(255).endsWith(".pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarioId", "talentoId", "empresaId", "estado", "fecha", "curriculumId", "desconocido"})
    void rejectsExtraFieldsWithoutSavingAnyNewVersion(String field) throws Exception {
        var talent = talent();
        UUID original = uploaded(talent, PDF);
        var response = talent.multipart(BASE, List.of(
            new Part("archivo", "nuevo.pdf", PDF, "application/pdf"),
            new Part(field, null, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8), "text/plain")), true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(curriculums.current(talent.id).orElseThrow().id()).isEqualTo(original);
        assertThat(count(talent.id)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateFilesWrongFilePartAndQueryOverrides() throws Exception {
        var talent = talent();
        var file = new Part("archivo", "cv.pdf", PDF, "application/pdf");
        assertThat(talent.multipart(BASE, List.of(file, file), true).statusCode()).isEqualTo(400);
        assertThat(talent.multipart(BASE, List.of(file,
            new Part("curriculumId", "otro.pdf", PDF, "application/pdf")), true).statusCode()).isEqualTo(400);
        assertThat(talent.upload(BASE + "?usuarioId=" + UUID.randomUUID(), "cv.pdf", PDF, true).statusCode())
            .isEqualTo(400);
        assertThat(talent.multipart(BASE, List.of(), true).statusCode()).isEqualTo(400);
        assertThat(curriculums.current(talent.id)).isEmpty();
        assertThat(count(talent.id)).isZero();
    }

    @Test
    void uploadAndDownloadRequireAuthenticationAndUploadRequiresCsrf() throws Exception {
        var visitor = new Browser();
        assertThat(visitor.get(BASE).statusCode()).isEqualTo(401);
        assertThat(visitor.upload(BASE, "cv.pdf", PDF, true).statusCode()).isEqualTo(401);
        var talent = talent();
        assertThat(talent.upload(BASE, "cv.pdf", PDF, false).statusCode()).isEqualTo(403);
        assertThat(count(talent.id)).isZero();
        uploaded(talent, PDF);
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", talent.id);
        assertThat(talent.get(BASE).statusCode()).isIn(401, 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RECLUTADOR", "EDITOR"})
    void staffCannotUploadOrUseOwnTalentDownloadRoute(String role) throws Exception {
        var staff = staff(role);
        assertThat(staff.upload(BASE, "cv.pdf", PDF, true).statusCode()).isEqualTo(403);
        assertThat(staff.get(BASE).statusCode()).isEqualTo(403);
        assertThat(count(staff.id)).isZero();
    }

    @Test
    void rs18LogsForbiddenFieldNameWithoutSubmittedValueOrPrivateStorageKey() throws Exception {
        var talent = talent();
        UUID id = uploaded(talent, PDF);
        String key = curriculums.find(id, talent.id).orElseThrow().claveAlmacen();
        String secret = "no-debe-aparecer-en-el-registro";
        Logger logger = (Logger) LoggerFactory.getLogger(RegistroAccesosEstructurado.LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(talent.multipart(BASE, List.of(
                new Part("archivo", "cv.pdf", PDF, "application/pdf"),
                new Part("curriculumId", null, secret.getBytes(StandardCharsets.UTF_8), "text/plain")), true)
                .statusCode()).isEqualTo(400);
            assertThat(appender.list).hasSize(1);
            String raw = appender.list.get(0).getFormattedMessage();
            JsonNode event = json.readTree(raw);
            assertThat(event.path("action").asText()).isEqualTo("curriculum.subir");
            assertThat(event.path("reason").asText()).isEqualTo(Denegaciones.FIELD_ACCESS_DENIED);
            assertThat(event.path("field").asText()).isEqualTo("curriculumId");
            assertThat(raw).doesNotContain(secret, key, PASSWORD, "Curriculum original");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private int count(UUID usuarioId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM curriculum WHERE usuario_id = ?", Integer.class, usuarioId);
    }

    private UUID uploaded(Browser browser, byte[] content) throws Exception {
        var response = browser.upload(BASE, "cv.pdf", content, true);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private Browser talent() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var browser = new Browser();
        assertThat(browser.send("/auth/register", json.writeValueAsString(
            new RegisterRequest(email, "Ana", "Prueba", PASSWORD)), "application/json", true).statusCode())
            .isEqualTo(202);
        assertThat(browser.login(email).statusCode()).isEqualTo(204);
        browser.id = accounts.byEmail(email).orElseThrow().id();
        return browser;
    }

    private Browser staff(String role) throws Exception {
        String adminEmail = UUID.randomUUID() + "@example.test";
        UUID company = provisioner.create("Empresa CV " + UUID.randomUUID(),
            new RegisterRequest(adminEmail, "Admin", "Prueba", PASSWORD), "test-suite");
        String email = adminEmail;
        UUID id = accounts.byEmail(email).orElseThrow().id();
        if (!"ADMIN".equals(role)) {
            email = UUID.randomUUID() + "@example.test";
            id = accounts.create(email, "Staff", "Prueba", passwords.encode(PASSWORD), "STAFF");
            jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')",
                id, company, role);
        }
        var browser = new Browser();
        assertThat(browser.login(email).statusCode()).isEqualTo(204);
        browser.id = id;
        return browser;
    }

    private record Part(String name, String fileName, byte[] content, String mime) {}

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api" + path);
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        UUID id;

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<byte[]> getBytes(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<String> upload(String path, String fileName, byte[] content, boolean csrf) throws Exception {
            return multipart(path, List.of(new Part("archivo", fileName, content, "application/octet-stream")), csrf);
        }

        HttpResponse<String> multipart(String path, List<Part> parts, boolean csrf) throws Exception {
            String boundary = "xp" + UUID.randomUUID();
            var out = new ByteArrayOutputStream();
            for (Part part : parts) {
                String disposition = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + part.name() + "\"";
                if (part.fileName() != null) {
                    disposition += "; filename=\"" + part.fileName() + "\"";
                }
                out.write((disposition + "\r\nContent-Type: " + part.mime() + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
                out.write(part.content());
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return send(path, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()),
                "multipart/form-data; boundary=" + boundary, csrf);
        }

        HttpResponse<String> login(String email) throws Exception {
            return send("/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", true);
        }

        HttpResponse<String> send(String path, String body, String contentType, boolean csrf) throws Exception {
            return send(path, HttpRequest.BodyPublishers.ofString(body), contentType, csrf);
        }

        HttpResponse<String> send(String path, HttpRequest.BodyPublisher body, String contentType, boolean csrf)
                throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType);
            if (csrf) {
                JsonNode token = json.readTree(get("/auth/csrf").body());
                request.header(token.path("headerName").asText(), token.path("token").asText());
            }
            return client.send(request.POST(body).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
