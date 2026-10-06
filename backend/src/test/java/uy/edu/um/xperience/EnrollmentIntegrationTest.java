package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import static org.assertj.core.api.Assertions.*;

/** RF4 — inscripción a cursos/proyectos y seguimiento de avance del Talento. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String ENROLLMENTS = "/enrollments";
    private static final String MANAGE = "/company/courses";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired JdbcTemplate jdbc;

    @Test
    void talentCanEnrollInCourseAssociatedToSelfAndListed() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Inscripción Curso");
        var talent = talent();
        var created = talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        assertThat(body.path("cursoId").asText()).isEqualTo(courseId.toString());
        assertThat(body.path("tipo").asText()).isEqualTo("CURSO");
        assertThat(body.path("titulo").asText()).isEqualTo("Inscripción Curso");
        assertThat(body.path("porcentajeAvance").asInt()).isEqualTo(0);

        UUID talentId = accounts.byEmail(talent.email).orElseThrow().id();
        assertThat(jdbc.queryForObject(
            "SELECT talento_id FROM inscripcion WHERE id = ?", UUID.class,
            UUID.fromString(body.path("id").asText()))).isEqualTo(talentId);

        JsonNode items = items(talent.get(ENROLLMENTS + "/me"));
        assertThat(items).anySatisfy(n -> {
            assertThat(n.path("id").asText()).isEqualTo(body.path("id").asText());
            assertThat(n.path("cursoId").asText()).isEqualTo(courseId.toString());
            assertThat(n.path("tipo").asText()).isEqualTo("CURSO");
        });
    }

    @Test
    void talentCanEnrollInProject() throws Exception {
        var admin = admin(company());
        UUID projectId = publish(admin, "PROYECTO", "Inscripción Proyecto");
        var talent = talent();
        var created = talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", projectId.toString())));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        assertThat(body.path("tipo").asText()).isEqualTo("PROYECTO");
        assertThat(body.path("titulo").asText()).isEqualTo("Inscripción Proyecto");
        assertThat(items(talent.get(ENROLLMENTS + "/me")))
            .anyMatch(n -> projectId.toString().equals(n.path("cursoId").asText()));
    }

    @Test
    void talentCanUpdateOwnProgressIncludingZeroAndHundred() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Avance propio");
        var talent = talent();
        UUID enrollmentId = enroll(talent, courseId);

        var zero = talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 0)));
        assertThat(zero.statusCode()).isEqualTo(200);
        assertThat(json.readTree(zero.body()).path("porcentajeAvance").asInt()).isEqualTo(0);

        var mid = talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 55)));
        assertThat(mid.statusCode()).isEqualTo(200);
        assertThat(json.readTree(mid.body()).path("porcentajeAvance").asInt()).isEqualTo(55);

        var full = talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 100)));
        assertThat(full.statusCode()).isEqualTo(200);
        assertThat(json.readTree(full.body()).path("porcentajeAvance").asInt()).isEqualTo(100);

        assertThat(jdbc.queryForObject(
            "SELECT porcentaje_avance FROM inscripcion WHERE id = ?", Integer.class, enrollmentId)).isEqualTo(100);
    }

    @Test
    void negativeProgressRejected() throws Exception {
        var enrollmentId = enrollFresh("CURSO", "Negativo");
        var talent = lastTalent;
        assertThat(talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", -1))).statusCode()).isEqualTo(400);
        assertPersistedProgress(enrollmentId, 0);
    }

    @Test
    void progressAboveHundredRejected() throws Exception {
        var enrollmentId = enrollFresh("CURSO", "Sobre100");
        var talent = lastTalent;
        assertThat(talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 101))).statusCode()).isEqualTo(400);
        assertPersistedProgress(enrollmentId, 0);
    }

    @Test
    void invalidProgressTypeRejected() throws Exception {
        var enrollmentId = enrollFresh("CURSO", "Tipo inválido");
        var talent = lastTalent;
        assertThat(talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            "{\"porcentajeAvance\":\"cincuenta\"}").statusCode()).isEqualTo(400);
        assertThat(talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            "{\"porcentajeAvance\":12.5}").statusCode()).isEqualTo(400);
        assertThat(talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            "{\"porcentajeAvance\":true}").statusCode()).isEqualTo(400);
        assertPersistedProgress(enrollmentId, 0);
    }

    @Test
    void duplicateCourseEnrollmentRejectedAndUniqueConstraintHolds() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Duplicado curso");
        var talent = talent();
        assertThat(talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())))
            .statusCode()).isEqualTo(201);
        assertThat(talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())))
            .statusCode()).isEqualTo(409);
        UUID talentId = accounts.byEmail(talent.email).orElseThrow().id();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM inscripcion WHERE talento_id = ? AND curso_id = ?",
            Integer.class, talentId, courseId)).isEqualTo(1);
    }

    @Test
    void duplicateProjectEnrollmentRejected() throws Exception {
        var admin = admin(company());
        UUID projectId = publish(admin, "PROYECTO", "Duplicado proyecto");
        var talent = talent();
        assertThat(talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", projectId.toString())))
            .statusCode()).isEqualTo(201);
        assertThat(talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", projectId.toString())))
            .statusCode()).isEqualTo(409);
    }

    @Test
    void talentCannotUpdateOrReadAnotherTalentEnrollment() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Ajeno");
        var talentA = talent();
        UUID enrollmentA = enroll(talentA, courseId);
        var talentB = talent();

        assertThat(talentB.send("PATCH", ENROLLMENTS + "/" + enrollmentA + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 80))).statusCode()).isIn(401, 403);
        assertPersistedProgress(enrollmentA, 0);

        assertThat(talentB.get(ENROLLMENTS + "/" + enrollmentA).statusCode()).isIn(401, 403);
        JsonNode itemsB = items(talentB.get(ENROLLMENTS + "/me"));
        assertThat(itemsB).noneMatch(n -> enrollmentA.toString().equals(n.path("id").asText()));
    }

    @Test
    void unauthenticatedEnrollmentRequestsRejected() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Sin auth");
        var anonymous = new Browser();
        assertThat(anonymous.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())))
            .statusCode()).isIn(401, 403);
        assertThat(anonymous.get(ENROLLMENTS + "/me").statusCode()).isIn(401, 403);
        assertThat(anonymous.send("PATCH", ENROLLMENTS + "/" + UUID.randomUUID() + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 10))).statusCode()).isIn(401, 403);
    }

    @Test
    void unauthorizedFieldsOnProgressUpdateRejected() throws Exception {
        var enrollmentId = enrollFresh("CURSO", "Campos extra");
        var talent = lastTalent;
        ObjectNode payload = json.createObjectNode();
        payload.put("porcentajeAvance", 40);
        payload.put("talentoId", UUID.randomUUID().toString());
        payload.put("cursoId", UUID.randomUUID().toString());
        assertThat(talent.send("PATCH", ENROLLMENTS + "/" + enrollmentId + "/progress",
            json.writeValueAsString(payload)).statusCode()).isEqualTo(400);
        assertPersistedProgress(enrollmentId, 0);
    }

    @Test
    void uniqueConstraintPreventsConcurrentDuplicateInsert() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Carrera unicidad");
        var talent = talent();
        UUID talentId = accounts.byEmail(talent.email).orElseThrow().id();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO inscripcion (id, talento_id, curso_id, porcentaje_avance)
            VALUES (?, ?, ?, 0)
            """, first, talentId, courseId);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO inscripcion (id, talento_id, curso_id, porcentaje_avance)
            VALUES (?, ?, ?, 0)
            """, second, talentId, courseId))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM inscripcion WHERE talento_id = ? AND curso_id = ?",
            Integer.class, talentId, courseId)).isEqualTo(1);
    }

    // ---- apoyo ----

    private Browser lastTalent;

    private UUID enrollFresh(String type, String title) throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, type, title);
        lastTalent = talent();
        return enroll(lastTalent, courseId);
    }

    private UUID enroll(Browser talent, UUID courseId) throws Exception {
        var response = talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private void assertPersistedProgress(UUID enrollmentId, int expected) {
        assertThat(jdbc.queryForObject(
            "SELECT porcentaje_avance FROM inscripcion WHERE id = ?", Integer.class, enrollmentId))
            .isEqualTo(expected);
    }

    private UUID publish(Browser admin, String type, String title) throws Exception {
        UUID id = create(admin, type, title);
        if ("CURSO".equals(type)) {
            assertThat(admin.send("POST", MANAGE + "/" + id + "/lessons",
                json.writeValueAsString(Map.of("titulo", "L1", "cuerpo", "Cuerpo"))).statusCode()).isEqualTo(200);
        } else {
            assertThat(admin.send("POST", MANAGE + "/" + id + "/deliverables",
                json.writeValueAsString(Map.of("titulo", "E1", "consigna", "Hacé X", "pista", "Pista",
                    "respuestasAceptadas", List.of("ok")))).statusCode()).isEqualTo(200);
        }
        assertThat(admin.send("POST", MANAGE + "/" + id + "/publish", "").statusCode()).isEqualTo(200);
        return id;
    }

    private UUID create(Browser client, String type, String title) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("tipo", type);
        body.put("titulo", title);
        body.put("descripcion", "Descripción");
        body.put("tecnologia", "Java");
        body.put("nivel", "INICIAL");
        body.put("duracionHoras", 10);
        body.put("costo", new java.math.BigDecimal("0"));
        var response = client.send("POST", MANAGE, json.writeValueAsString(body));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private JsonNode items(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("items");
    }

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

    private Browser talent() throws Exception {
        String email = email();
        var client = new Browser();
        client.email = email;
        assertThat(client.register(email).statusCode()).isEqualTo(202);
        assertThat(client.login(email).statusCode()).isEqualTo(204);
        return client;
    }

    private Browser login(String email) throws Exception {
        var client = new Browser();
        client.email = email;
        assertThat(client.login(email).statusCode()).isEqualTo(204);
        return client;
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private class Browser {
        String email;
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return send(method, path, HttpRequest.BodyPublishers.ofString(body), "application/json");
        }

        HttpResponse<String> register(String mail) throws Exception {
            var body = json.writeValueAsString(Map.of(
                "nombre", "Ana", "apellido", "Prueba", "correo", mail, "password", PASSWORD));
            return send("POST", "/auth/register", HttpRequest.BodyPublishers.ofString(body), "application/json");
        }

        HttpResponse<String> login(String mail) throws Exception {
            return send("POST", "/auth/login", HttpRequest.BodyPublishers.ofString(
                "correo=" + URLEncoder.encode(mail, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)),
                "application/x-www-form-urlencoded");
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
