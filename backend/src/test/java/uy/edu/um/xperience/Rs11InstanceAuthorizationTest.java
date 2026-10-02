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
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.profile.TalentProfile;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.AuthorizationService;
import uy.edu.um.xperience.security.ResourceAccess;
import uy.edu.um.xperience.security.Sujeto;
import static org.assertj.core.api.Assertions.*;

/**
 * RS11 — Autorización por instancia de recurso.
 * Cubre los recursos con rutas reales hoy: perfil propio y cursos/proyectos/entregables/materiales
 * de la empresa. Inscripción, progreso, postulación y entregable del Talento aún no tienen API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Rs11InstanceAuthorizationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String COURSES = "/company/courses";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationService policy;

    @Test
    void talentACannotReadOrWriteTalentBProfileByIdentifier() throws Exception {
        var talentA = registerTalent();
        var talentB = registerTalent();
        UUID idB = talentB.id;

        var byUrl = talentA.get("/profile/" + idB);
        var byUrlWrite = talentA.patch("/profile/" + idB, profileBody(talentA.email), true);
        assertThat(byUrl.statusCode()).isEqualTo(403);
        assertThat(byUrlWrite.statusCode()).isEqualTo(403);

        ObjectNode attack = profilePayload(talentA.email);
        attack.put("nombre", "Intruso");
        attack.put("userId", idB.toString());
        attack.put("usuarioId", idB.toString());
        assertThat(talentA.patch("/profile/me", json.writeValueAsString(attack), true).statusCode()).isEqualTo(400);

        JsonNode profileB = json.readTree(talentB.get("/profile/me").body());
        assertThat(profileB.path("nombre").asText()).isEqualTo("Ana");
        assertThat(accounts.byEmail(talentB.email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void talentCanAccessOwnProfileAsCounterproof() throws Exception {
        var talent = registerTalent();
        var response = talent.get("/profile/me");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("correo").asText()).isEqualTo(talent.email);

        ObjectNode update = profilePayload(talent.email);
        update.put("nombre", "Propio");
        assertThat(talent.patch("/profile/me", json.writeValueAsString(update), true).statusCode()).isEqualTo(200);
        assertThat(json.readTree(talent.get("/profile/me").body()).path("nombre").asText()).isEqualTo("Propio");
    }

    @Test
    void evaluatorDeniesForeignProfileInstanceEvenWhenActionIsGranted() {
        var talentA = accounts.byEmail(registerAndStore()).orElseThrow();
        var talentB = accounts.byEmail(registerAndStore()).orElseThrow();
        Sujeto subjectA = Sujeto.from(talentA);
        TalentProfile profileB = new TalentProfile(talentB.id(), talentB.correo(), talentB.nombre(),
            talentB.apellido(), null, List.of(), true, true);

        assertThat(policy.puedeInvocar(subjectA, "perfil.gestionar")).isTrue();
        assertThat(policy.autorizar(subjectA, "perfil.gestionar", ResourceAccess.own(talentA.id()))).isTrue();
        assertThat(policy.autorizar(subjectA, "perfil.gestionar", profileB)).isFalse();
        assertThat(policy.autorizar(subjectA, "perfil.gestionar", ResourceAccess.own(talentB.id()))).isFalse();
    }

    @Test
    void staffCannotReachAnotherCompanyCourseOrNestedDeliverable() throws Exception {
        UUID companyA = company();
        UUID companyB = company();
        var editorA = member(companyA, "EDITOR");
        var editorB = member(companyB, "EDITOR");

        UUID projectId = create(editorA, "PROYECTO", "Proyecto A");
        String deliverableId = addDeliverable(editorA, projectId).path("entregables").get(0).path("id").asText();

        var foreignCourse = editorB.get(COURSES + "/" + projectId);
        var missingCourse = editorB.get(COURSES + "/" + UUID.randomUUID());
        assertThat(foreignCourse.statusCode()).isEqualTo(403);
        assertThat(foreignCourse.body()).isEqualTo(missingCourse.body());

        assertThat(editorB.send("PUT", COURSES + "/" + projectId + "/deliverables/" + deliverableId,
            deliverableBody()).statusCode()).isEqualTo(403);
        assertThat(editorB.send("DELETE", COURSES + "/" + projectId + "/deliverables/" + deliverableId, "")
            .statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entregable WHERE id = ?", Integer.class,
            UUID.fromString(deliverableId))).isEqualTo(1);
    }

    @Test
    void knowingADeliverableIdDoesNotAllowAccessThroughAnotherProjectOfSameCompany() throws Exception {
        UUID company = company();
        var editor = member(company, "EDITOR");
        UUID projectA = create(editor, "PROYECTO", "Proyecto con entregable");
        UUID projectB = create(editor, "PROYECTO", "Otro proyecto");
        String deliverableA = addDeliverable(editor, projectA).path("entregables").get(0).path("id").asText();

        // Acceso legítimo al proyecto A (contraprueba del camino indirecto).
        assertThat(editor.get(COURSES + "/" + projectA).statusCode()).isEqualTo(200);

        // Camino indirecto: usar el id del entregable de A bajo la ruta del proyecto B.
        var nested = editor.send("PUT", COURSES + "/" + projectB + "/deliverables/" + deliverableA,
            deliverableBody());
        var missing = editor.send("PUT", COURSES + "/" + projectB + "/deliverables/" + UUID.randomUUID(),
            deliverableBody());
        assertThat(nested.statusCode()).isEqualTo(403);
        assertThat(nested.body()).isEqualTo(missing.body());
        assertThat(jdbc.queryForObject("SELECT curso_id FROM entregable WHERE id = ?", UUID.class,
            UUID.fromString(deliverableA))).isEqualTo(projectA);
    }

    @Test
    void staffCanAccessOwnCompanyCourseAsCounterproof() throws Exception {
        UUID company = company();
        var editor = member(company, "EDITOR");
        UUID id = create(editor, "CURSO", "Propio");
        assertThat(editor.get(COURSES + "/" + id).statusCode()).isEqualTo(200);
        assertThat(json.readTree(editor.get(COURSES).body())).hasSize(1);
    }

    @Test
    void resourcesWithoutImplementedApisRemainDeniedAtPolicyLevel() {
        // Documenta el alcance pendiente: no hay tablas/rutas de inscripción ni postulación.
        String email = registerAndStore();
        Sujeto talent = Sujeto.from(accounts.byEmail(email).orElseThrow());
        UUID foreign = UUID.randomUUID();

        assertThat(policy.puedeInvocar(talent, "inscripcion.ver")).isTrue();
        assertThat(policy.autorizar(talent, "inscripcion.ver", ResourceAccess.own(foreign))).isFalse();
        assertThat(policy.puedeInvocar(talent, "postulacion.ver")).isTrue();
        assertThat(policy.autorizar(talent, "postulacion.ver", ResourceAccess.own(foreign))).isFalse();
        assertThat(policy.autorizar(talent, "entregable.responder", ResourceAccess.own(foreign))).isFalse();
        assertThat(policy.autorizar(talent, "material.descargar", ResourceAccess.own(foreign))).isFalse();
    }

    // ---- Apoyo ----

    private Browser registerTalent() throws Exception {
        String email = email();
        var client = new Browser(email);
        client.register(email);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
    }

    private String registerAndStore() {
        String email = email();
        try {
            var client = new Browser(email);
            client.register(email);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        return email;
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

    private JsonNode addDeliverable(Browser client, UUID projectId) throws Exception {
        var response = client.send("POST", COURSES + "/" + projectId + "/deliverables", deliverableBody());
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

    private String deliverableBody() throws Exception {
        return json.writeValueAsString(Map.of("titulo", "Endpoint", "consigna", "¿Qué devuelve GET /ping?",
            "pista", "Mirá el controlador", "respuestasAceptadas", List.of("pong")));
    }

    private ObjectNode profilePayload(String email) {
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

    private String profileBody(String email) throws Exception {
        return json.writeValueAsString(profilePayload(email));
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String email;
        UUID id;

        Browser(String email) { this.email = email; }

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

        HttpResponse<String> patch(String path, String body, boolean csrf) throws Exception {
            return write("PATCH", path, body, csrf);
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return write(method, path, body, true);
        }

        private HttpResponse<String> write(String method, String path, String body, boolean csrf) throws Exception {
            return post(path, body, "application/json", csrf, method);
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
    }
}
