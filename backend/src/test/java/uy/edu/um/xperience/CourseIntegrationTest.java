package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
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
import uy.edu.um.xperience.course.AnswerNormalizer;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import static org.assertj.core.api.Assertions.*;

/** RF5 (gestión de cursos y proyectos) y R9 (mínimo privilegio por rol) sobre el servidor real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CourseIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String BASE = "/company/courses";
    private static final byte[] PDF = "%PDF-1.4\n% prueba\n".getBytes(StandardCharsets.US_ASCII);
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    // ---- RF5 ----

    @Test
    void editorCreatesDraftForOwnCompanyAndSeesItListed() throws Exception {
        UUID company = company();
        var editor = member(company, "EDITOR");
        var created = editor.send("POST", BASE, course("CURSO", "Java desde cero"));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        assertThat(body.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(body.path("tipo").asText()).isEqualTo("CURSO");
        UUID id = UUID.fromString(body.path("id").asText());
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM curso WHERE id = ?", UUID.class, id)).isEqualTo(company);
        assertThat(created.body()).doesNotContain("empresa", "creado_por", "creadoPor");

        JsonNode list = json.readTree(editor.get(BASE).body());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("titulo").asText()).isEqualTo("Java desde cero");
    }

    @Test
    void adminAlsoManagesContent() throws Exception {
        var admin = admin(company());
        UUID id = create(admin, "CURSO", "Spring");
        addLesson(admin, id);
        var published = admin.send("POST", BASE + "/" + id + "/publish", "");
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(json.readTree(published.body()).path("estado").asText()).isEqualTo("PUBLICADO");
    }

    @Test
    void lifecycleDraftPublishedAndTakenDown() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Docker");
        assertThat(editor.send("POST", BASE + "/" + id + "/publish", "").statusCode()).isEqualTo(409);

        String lesson = addLesson(editor, id).path("lecciones").get(0).path("id").asText();
        assertThat(editor.send("POST", BASE + "/" + id + "/publish", "").statusCode()).isEqualTo(200);
        // Publicado: puede haber inscriptos, así que no se borra ni se le quitan lecciones.
        assertThat(editor.send("DELETE", BASE + "/" + id, "").statusCode()).isEqualTo(409);
        assertThat(editor.send("DELETE", BASE + "/" + id + "/lessons/" + lesson, "").statusCode()).isEqualTo(409);
        assertThat(editor.send("PUT", BASE + "/" + id, data("Editado en publicado")).statusCode()).isEqualTo(200);

        var down = editor.send("POST", BASE + "/" + id + "/unpublish", "");
        assertThat(json.readTree(down.body()).path("estado").asText()).isEqualTo("BAJADO");
        assertThat(editor.send("PUT", BASE + "/" + id, data("Otro")).statusCode()).isEqualTo(409);
        assertThat(editor.send("POST", BASE + "/" + id + "/lessons", lessonBody()).statusCode()).isEqualTo(409);
        assertThat(editor.send("POST", BASE + "/" + id + "/publish", "").statusCode()).isEqualTo(200);
    }

    @Test
    void draftCanBeDeletedWithItsMaterial() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Borrable");
        assertThat(editor.upload(BASE + "/" + id + "/materials", "guia.pdf", PDF).statusCode()).isEqualTo(200);
        long files = storedFiles();
        assertThat(editor.send("DELETE", BASE + "/" + id, "").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curso WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE curso_id = ?", Integer.class, id)).isZero();
        assertThat(storedFiles()).isEqualTo(files - 1);
    }

    @Test
    void lessonsAreRenumberedWhenOneIsDeleted() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Numeración");
        addLesson(editor, id);
        addLesson(editor, id);
        String first = addLesson(editor, id).path("lecciones").get(0).path("id").asText();
        var response = editor.send("DELETE", BASE + "/" + id + "/lessons/" + first, "");
        JsonNode lessons = json.readTree(response.body()).path("lecciones");
        assertThat(lessons).hasSize(2);
        assertThat(lessons.get(0).path("numero").asInt()).isEqualTo(1);
        assertThat(lessons.get(1).path("numero").asInt()).isEqualTo(2);
    }

    @Test
    void courseHasNoDeliverablesAndProjectHasNoLessons() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID course = create(editor, "CURSO", "Curso");
        UUID project = create(editor, "PROYECTO", "Proyecto");
        assertThat(editor.send("POST", BASE + "/" + course + "/deliverables", deliverable("r")).statusCode()).isEqualTo(409);
        assertThat(editor.send("POST", BASE + "/" + project + "/lessons", lessonBody()).statusCode()).isEqualTo(409);
    }

    @Test
    void acceptedAnswersAreNormalizedHashedAndNeverReturned() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "PROYECTO", "API REST");
        String secret = "Respuesta   secreta 42";
        var created = editor.send("POST", BASE + "/" + id + "/deliverables", deliverable(secret, "  respuesta secreta 42 "));
        assertThat(created.statusCode()).isEqualTo(200);
        // Normalizadas son la misma respuesta: se guarda una.
        assertThat(json.readTree(created.body()).path("entregables").get(0).path("cantidadRespuestasAceptadas").asInt())
            .isEqualTo(1);
        String deliverableId = json.readTree(created.body()).path("entregables").get(0).path("id").asText();
        String hash = jdbc.queryForObject("SELECT hash FROM respuesta_aceptada WHERE entregable_id = ?",
            String.class, UUID.fromString(deliverableId));
        assertThat(passwords.matches(AnswerNormalizer.normalize("RESPUESTA secreta 42"), hash)).isTrue();
        for (String response : List.of(created.body(), editor.get(BASE + "/" + id).body())) {
            assertThat(response).doesNotContainIgnoringCase("secreta").doesNotContain(hash, "respuestasAceptadas\"");
        }

        var keep = Map.of("titulo", "Nuevo título", "consigna", "Consigna", "pista", "Pista");
        assertThat(editor.send("PUT", BASE + "/" + id + "/deliverables/" + deliverableId, json.writeValueAsString(keep))
            .statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT hash FROM respuesta_aceptada WHERE entregable_id = ?",
            String.class, UUID.fromString(deliverableId))).isEqualTo(hash);
    }

    @Test
    void materialIsUploadedAndDownloadedOnlyThroughTheBackend() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Con material");
        var upload = editor.upload(BASE + "/" + id + "/materials", "../../guia.pdf", PDF);
        assertThat(upload.statusCode()).isEqualTo(200);
        JsonNode material = json.readTree(upload.body()).path("materiales").get(0);
        assertThat(material.path("nombre").asText()).isEqualTo("guia.pdf");
        assertThat(material.path("tipoMime").asText()).isEqualTo("application/pdf");
        assertThat(upload.body()).doesNotContain("clave", "almacen");

        var download = editor.getBytes(BASE + "/" + id + "/materials/" + material.path("id").asText());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment");
        assertThat(download.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(download.body()).isEqualTo(PDF);
    }

    @Test
    void acceptsMaterialLargerThanTheFormPostLimit() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Material grande");
        byte[] big = Arrays.copyOf(PDF, 2 * 1024 * 1024);
        var upload = editor.upload(BASE + "/" + id + "/materials", "grande.pdf", big);
        assertThat(upload.statusCode()).isEqualTo(200);
        assertThat(json.readTree(upload.body()).path("materiales").get(0).path("tamanioBytes").asLong())
            .isEqualTo(big.length);
    }

    @Test
    void rejectsFilesOfUnsupportedTypeWhateverTheExtension() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Tipos");
        long files = storedFiles();
        var upload = editor.upload(BASE + "/" + id + "/materials", "script.pdf",
            "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        assertThat(upload.statusCode()).isEqualTo(400);
        assertThat(storedFiles()).isEqualTo(files);
    }

    @Test
    void writesRejectFieldsOutsideTheFormWithoutApplyingAnything() throws Exception {
        var editor = member(company(), "EDITOR");
        UUID id = create(editor, "CURSO", "Original");
        var attempt = new HashMap<String, Object>(json.readValue(data("Cambiado"), Map.class));
        attempt.put("estado", "PUBLICADO");
        assertThat(editor.send("PUT", BASE + "/" + id, json.writeValueAsString(attempt)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForMap("SELECT titulo, estado FROM curso WHERE id = ?", id))
            .containsEntry("titulo", "Original").containsEntry("estado", "BORRADOR");

        var other = new HashMap<String, Object>(json.readValue(course("CURSO", "Plantado"), Map.class));
        other.put("empresaId", UUID.randomUUID().toString());
        assertThat(editor.send("POST", BASE, json.writeValueAsString(other)).statusCode()).isEqualTo(400);
    }

    @Test
    void eachCompanyOnlyReachesItsOwnContent() throws Exception {
        var editorA = member(company(), "EDITOR");
        UUID id = create(editorA, "CURSO", "De la empresa A");
        String lesson = addLesson(editorA, id).path("lecciones").get(0).path("id").asText();
        var editorB = member(company(), "EDITOR");

        assertThat(json.readTree(editorB.get(BASE).body())).isEmpty();
        var foreign = editorB.get(BASE + "/" + id);
        var missing = editorB.get(BASE + "/" + UUID.randomUUID());
        assertThat(foreign.statusCode()).isEqualTo(403);
        assertThat(foreign.body()).isEqualTo(missing.body());
        assertThat(editorB.send("PUT", BASE + "/" + id, data("Pisado")).statusCode()).isEqualTo(403);
        assertThat(editorB.send("DELETE", BASE + "/" + id + "/lessons/" + lesson, "").statusCode()).isEqualTo(403);
        assertThat(editorB.send("DELETE", BASE + "/" + id, "").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT titulo FROM curso WHERE id = ?", String.class, id)).isEqualTo("De la empresa A");
    }

    @Test
    void anonymousVisitorIsRejected() throws Exception {
        var visitor = new Browser();
        assertThat(visitor.get(BASE).statusCode()).isEqualTo(401);
        assertThat(visitor.send("POST", BASE, course("CURSO", "x")).statusCode()).isEqualTo(401);
    }

    // ---- R9 ----

    @ParameterizedTest
    @ValueSource(strings = {"RECLUTADOR", "TALENTO"})
    void rolesWithoutContentPermissionsCannotRunAnyManagementOperation(String role) throws Exception {
        UUID company = company();
        var editor = member(company, "EDITOR");
        UUID id = create(editor, "CURSO", "Existente");
        String lesson = addLesson(editor, id).path("lecciones").get(0).path("id").asText();
        String material = json.readTree(editor.upload(BASE + "/" + id + "/materials", "a.pdf", PDF).body())
            .path("materiales").get(0).path("id").asText();
        var intruder = "TALENTO".equals(role) ? talent() : member(company, role);
        long files = storedFiles();

        List<HttpResponse<?>> responses = List.of(
            intruder.get(BASE),
            intruder.get(BASE + "/" + id),
            intruder.send("POST", BASE, course("CURSO", "Nuevo")),
            intruder.send("PUT", BASE + "/" + id, data("Pisado")),
            intruder.send("POST", BASE + "/" + id + "/publish", ""),
            intruder.send("POST", BASE + "/" + id + "/unpublish", ""),
            intruder.send("POST", BASE + "/" + id + "/lessons", lessonBody()),
            intruder.send("PUT", BASE + "/" + id + "/lessons/" + lesson, lessonBody()),
            intruder.send("DELETE", BASE + "/" + id + "/lessons/" + lesson, ""),
            intruder.upload(BASE + "/" + id + "/materials", "b.pdf", PDF),
            intruder.getBytes(BASE + "/" + id + "/materials/" + material),
            intruder.send("DELETE", BASE + "/" + id + "/materials/" + material, ""),
            intruder.send("DELETE", BASE + "/" + id, ""));
        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.request().method() + " " + r.uri()).isEqualTo(403));

        assertThat(jdbc.queryForMap("SELECT titulo, estado FROM curso WHERE id = ?", id))
            .containsEntry("titulo", "Existente").containsEntry("estado", "BORRADOR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curso WHERE empresa_id = ?", Integer.class, company)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leccion WHERE curso_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE curso_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(storedFiles()).isEqualTo(files);
    }

    @Test
    void losingTheEditorRoleTakesEffectOnTheOpenSession() throws Exception {
        UUID company = company();
        var editor = member(company, "EDITOR");
        assertThat(editor.get(BASE).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE membresia SET rol = 'RECLUTADOR' WHERE usuario_id = ?", editor.id);
        assertThat(editor.get(BASE).statusCode()).isEqualTo(403);
    }

    // ---- Apoyo ----

    private UUID company() {
        return provisioner.create("Empresa " + UUID.randomUUID(),
            new RegisterRequest(email(), "Admin", "Prueba", PASSWORD), "test-suite");
    }

    private Browser admin(UUID company) throws Exception {
        String email = jdbc.queryForObject("""
            SELECT u.correo FROM usuario u JOIN membresia m ON m.usuario_id = u.id
            WHERE m.empresa_id = ? AND m.rol = 'ADMIN'
            """, String.class, company);
        return login(email);
    }

    /** Miembro con la invitación ya aceptada y el rol asignado (RF7 es aparte). */
    private Browser member(UUID company, String role) throws Exception {
        String email = email();
        UUID id = accounts.create(email, "Staff", "Prueba", passwords.encode(PASSWORD), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')", id, company, role);
        return login(email);
    }

    private Browser talent() throws Exception {
        String email = email();
        var client = new Browser();
        client.send("POST", "/auth/register", json.writeValueAsString(new RegisterRequest(email, "Ana", "Prueba", PASSWORD)));
        client.login(email);
        return client;
    }

    private Browser login(String email) throws Exception {
        var client = new Browser();
        assertThat(client.login(email).statusCode()).isEqualTo(204);
        return client;
    }

    private UUID create(Browser client, String type, String title) throws Exception {
        var response = client.send("POST", BASE, course(type, title));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private JsonNode addLesson(Browser client, UUID id) throws Exception {
        var response = client.send("POST", BASE + "/" + id + "/lessons", lessonBody());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private String course(String type, String title) throws Exception {
        var body = new HashMap<String, Object>(json.readValue(data(title), Map.class));
        body.put("tipo", type);
        return json.writeValueAsString(body);
    }

    private String data(String title) throws Exception {
        return json.writeValueAsString(Map.of("titulo", title, "descripcion", "Descripción", "tecnologia", "Java",
            "nivel", "INICIAL", "duracionHoras", 10, "costo", 0));
    }

    private String lessonBody() throws Exception {
        return json.writeValueAsString(Map.of("titulo", "Introducción", "cuerpo", "Contenido de la lección"));
    }

    private String deliverable(String... answers) throws Exception {
        return json.writeValueAsString(Map.of("titulo", "Endpoint", "consigna", "¿Qué devuelve GET /ping?",
            "pista", "Mirá el controlador", "respuestasAceptadas", List.of(answers)));
    }

    private static long storedFiles() throws Exception {
        Path dir = Path.of("./target/test-archivos");
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

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
        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return send(method, path, HttpRequest.BodyPublishers.ofString(body), "application/json");
        }
        HttpResponse<String> upload(String path, String fileName, byte[] content) throws Exception {
            String boundary = "xp" + UUID.randomUUID();
            var out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + fileName
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return send("POST", path, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()),
                "multipart/form-data; boundary=" + boundary);
        }
        HttpResponse<String> login(String email) throws Exception {
            var response = send("POST", "/auth/login", HttpRequest.BodyPublishers.ofString(
                "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)),
                "application/x-www-form-urlencoded");
            id = accounts.byEmail(email).map(a -> a.id()).orElse(null);
            return response;
        }
        private HttpResponse<String> send(String method, String path, HttpRequest.BodyPublisher body,
                                          String contentType) throws Exception {
            var token = json.readTree(get("/auth/csrf").body());
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType)
                .header(token.path("headerName").asText(), token.path("token").asText())
                .method(method, body).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
