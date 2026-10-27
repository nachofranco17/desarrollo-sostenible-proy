package uy.edu.um.xperience;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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
import uy.edu.um.xperience.provision.CompanyProvisioner;
import static org.assertj.core.api.Assertions.*;

/**
 * RS13 — Identificadores que no funcionan como llave de acceso.
 * Reutiliza el PEP y la autorización por instancia (RS11): conocer o manipular un UUID
 * nunca basta; la relación con el sujeto de la sesión debe verificarse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Rs13IdentifierNotAccessKeyTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String ENROLLMENTS = "/enrollments";
    private static final String COURSES = "/company/courses";
    private static final String OFFERS = "/company/offers";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @Test
    void talentCanAccessOwnEnrollmentById() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Propia RS13");
        var talent = registerTalent();
        UUID enrollmentId = enroll(talent, courseId);

        var response = talent.get(ENROLLMENTS + "/" + enrollmentId);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("id").asString()).isEqualTo(enrollmentId.toString());
        assertThat(json.readTree(response.body()).path("cursoId").asString()).isEqualTo(courseId.toString());
    }

    @Test
    void knowingExactForeignEnrollmentIdDoesNotGrantAccess() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Ajeno RS13");
        var talentA = registerTalent();
        var talentB = registerTalent();
        UUID enrollmentB = enroll(talentB, courseId);

        var read = talentA.get(ENROLLMENTS + "/" + enrollmentB);
        var progress = talentA.send("PATCH", ENROLLMENTS + "/" + enrollmentB + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 40)));

        assertThat(read.statusCode()).isEqualTo(403);
        assertThat(progress.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject(
            "SELECT porcentaje_avance FROM inscripcion WHERE id = ?", Integer.class, enrollmentB)).isEqualTo(0);
        assertThat(json.readTree(talentA.get(ENROLLMENTS + "/me").body()).path("items"))
            .noneMatch(n -> enrollmentB.toString().equals(n.path("id").asString()));
    }

    @Test
    void swappingOwnUuidForAnotherValidForeignUuidIsDenied() throws Exception {
        var admin = admin(company());
        UUID courseA = publish(admin, "CURSO", "Curso A RS13");
        UUID courseB = publish(admin, "CURSO", "Curso B RS13");
        var talentA = registerTalent();
        var talentB = registerTalent();
        UUID enrollmentA = enroll(talentA, courseA);
        UUID enrollmentB = enroll(talentB, courseB);

        assertThat(talentA.get(ENROLLMENTS + "/" + enrollmentA).statusCode()).isEqualTo(200);

        var swapped = talentA.get(ENROLLMENTS + "/" + enrollmentB);
        var swappedProgress = talentA.send("PATCH", ENROLLMENTS + "/" + enrollmentB + "/progress",
            json.writeValueAsString(Map.of("porcentajeAvance", 90)));

        assertThat(swapped.statusCode()).isEqualTo(403);
        assertThat(swappedProgress.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject(
            "SELECT porcentaje_avance FROM inscripcion WHERE id = ?", Integer.class, enrollmentB)).isEqualTo(0);
    }

    @Test
    void nonexistentEnrollmentIdIsIndistinguishableFromForeign() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Inexistente RS13");
        var talentA = registerTalent();
        var talentB = registerTalent();
        UUID enrollmentB = enroll(talentB, courseId);
        UUID missing = UUID.randomUUID();

        var foreign = talentA.get(ENROLLMENTS + "/" + enrollmentB);
        var unknown = talentA.get(ENROLLMENTS + "/" + missing);

        assertThat(foreign.statusCode()).isEqualTo(403);
        assertThat(unknown.statusCode()).isEqualTo(403);
        assertThat(foreign.body()).isEqualTo(unknown.body());
    }

    @Test
    void ownMeEndpointsWorkWithoutClientSuppliedIdentifier() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Me RS13");
        var talent = registerTalent();
        UUID enrollmentId = enroll(talent, courseId);

        var profile = talent.get("/profile/me");
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(profile.body()).path("correo").asString()).isEqualTo(talent.email);

        var list = talent.get(ENROLLMENTS + "/me");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json.readTree(list.body()).path("items"))
            .anyMatch(n -> enrollmentId.toString().equals(n.path("id").asString()));

        assertThat(talent.get("/profile/" + talent.id).statusCode()).isEqualTo(403);
    }

    @Test
    void clientSuppliedUserIdDoesNotChangeEffectiveOwner() throws Exception {
        var admin = admin(company());
        UUID courseId = publish(admin, "CURSO", "Owner body RS13");
        var talentA = registerTalent();
        var talentB = registerTalent();

        ObjectNode enrollAttack = json.createObjectNode();
        enrollAttack.put("cursoId", courseId.toString());
        enrollAttack.put("talentoId", talentB.id.toString());
        enrollAttack.put("usuarioId", talentB.id.toString());
        assertThat(talentA.send("POST", ENROLLMENTS, json.writeValueAsString(enrollAttack)).statusCode())
            .isEqualTo(400);

        var clean = talentA.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())));
        assertThat(clean.statusCode()).isEqualTo(201);
        UUID enrollmentId = UUID.fromString(json.readTree(clean.body()).path("id").asString());
        assertThat(jdbc.queryForObject(
            "SELECT talento_id FROM inscripcion WHERE id = ?", UUID.class, enrollmentId)).isEqualTo(talentA.id);

        ObjectNode profileAttack = profilePayload(talentA.email);
        profileAttack.put("nombre", "Intruso");
        profileAttack.put("userId", talentB.id.toString());
        profileAttack.put("usuarioId", talentB.id.toString());
        assertThat(talentA.patch("/profile/me", json.writeValueAsString(profileAttack), true).statusCode())
            .isEqualTo(400);
        assertThat(json.readTree(talentB.get("/profile/me").body()).path("nombre").asString()).isEqualTo("Ana");
    }

    @Test
    void nestedRoutesDoNotAllowForeignResourceByKnownId() throws Exception {
        UUID companyId = company();
        var editor = member(companyId, "EDITOR");
        UUID projectA = createCourse(editor, "PROYECTO", "Proyecto A RS13");
        UUID projectB = createCourse(editor, "PROYECTO", "Proyecto B RS13");
        String deliverableA = addDeliverable(editor, projectA).path("entregables").get(0).path("id").asString();

        var nested = editor.send("PUT", COURSES + "/" + projectB + "/deliverables/" + deliverableA,
            deliverableBody());
        var missing = editor.send("PUT", COURSES + "/" + projectB + "/deliverables/" + UUID.randomUUID(),
            deliverableBody());
        assertThat(nested.statusCode()).isEqualTo(403);
        assertThat(nested.body()).isEqualTo(missing.body());
        assertThat(jdbc.queryForObject("SELECT curso_id FROM entregable WHERE id = ?", UUID.class,
            UUID.fromString(deliverableA))).isEqualTo(projectA);

        var admin = admin(companyId);
        UUID offerA = createAndPublishOffer(admin, "Oferta A RS13");
        UUID offerB = createAndPublishOffer(admin, "Oferta B RS13");
        var talent = registerTalent();
        uploadCurriculum(talent);
        UUID applicationA = apply(talent, offerA);

        var profileUnderWrongOffer = admin.get(
            OFFERS + "/" + offerB + "/applications/" + applicationA + "/profile");
        var unknownNested = admin.get(
            OFFERS + "/" + offerB + "/applications/" + UUID.randomUUID() + "/profile");
        assertThat(profileUnderWrongOffer.statusCode()).isEqualTo(403);
        assertThat(profileUnderWrongOffer.body()).isEqualTo(unknownNested.body());
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

    private UUID company() {
        return provisioner.create("Empresa " + UUID.randomUUID(),
            new RegisterRequest(email(), "Admin", "Prueba", PASSWORD.toCharArray()), "test-suite");
    }

    private Browser admin(UUID company) throws Exception {
        String email = jdbc.queryForObject("""
            SELECT u.correo FROM usuario u JOIN membresia m ON m.usuario_id = u.id
            WHERE m.empresa_id = ? AND m.rol = 'ADMIN'
            """, String.class, company);
        var client = new Browser(email);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
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

    private UUID publish(Browser admin, String type, String title) throws Exception {
        UUID id = createCourse(admin, type, title);
        if ("CURSO".equals(type)) {
            assertThat(admin.send("POST", COURSES + "/" + id + "/lessons",
                json.writeValueAsString(Map.of("titulo", "L1", "cuerpo", "Cuerpo"))).statusCode()).isEqualTo(200);
        } else {
            addDeliverable(admin, id);
        }
        assertThat(admin.send("POST", COURSES + "/" + id + "/publish", "").statusCode()).isEqualTo(200);
        return id;
    }

    private UUID createCourse(Browser client, String type, String title) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("tipo", type);
        body.put("titulo", title);
        body.put("descripcion", "Descripción");
        body.put("tecnologia", "Java");
        body.put("nivel", "INICIAL");
        body.put("duracionHoras", 10);
        body.put("costo", 0);
        var response = client.send("POST", COURSES, json.writeValueAsString(body));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asString());
    }

    private JsonNode addDeliverable(Browser client, UUID projectId) throws Exception {
        var response = client.send("POST", COURSES + "/" + projectId + "/deliverables", deliverableBody());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private String deliverableBody() throws Exception {
        return json.writeValueAsString(Map.of("titulo", "Endpoint", "consigna", "¿Qué?",
            "pista", "Pista", "respuestasAceptadas", List.of("ok")));
    }

    private UUID createAndPublishOffer(Browser admin, String title) throws Exception {
        var created = admin.send("POST", OFFERS, json.writeValueAsString(Map.of(
            "titulo", title, "descripcion", "Desc", "cursosRequeridos", List.of())));
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(json.readTree(created.body()).path("id").asString());
        assertThat(admin.send("POST", OFFERS + "/" + id + "/publish", "").statusCode()).isEqualTo(200);
        return id;
    }

    private void uploadCurriculum(Browser talent) throws Exception {
        byte[] pdf = "%PDF-1.4\nRS13\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        var boundary = "----rs13" + UUID.randomUUID();
        var body = new byte[0];
        body = concat(body, ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"cv.pdf\"\r\n"
            + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body = concat(body, pdf);
        body = concat(body, ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var token = json.readTree(talent.get("/auth/csrf").body());
        var request = HttpRequest.newBuilder(uri("/profile/me/curriculum"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header(token.path("headerName").asString(), token.path("token").asString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        assertThat(talent.client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
    }

    private UUID apply(Browser talent, UUID offerId) throws Exception {
        var response = talent.send("POST", "/offers/" + offerId + "/applications", "{}");
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asString());
    }

    private UUID enroll(Browser talent, UUID courseId) throws Exception {
        var response = talent.send("POST", ENROLLMENTS, json.writeValueAsString(Map.of("cursoId", courseId.toString())));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asString());
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

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] out = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
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
                new RegisterRequest(mail, "Ana", "Prueba", PASSWORD.toCharArray())), "application/json", true);
        }

        HttpResponse<String> login(String mail, String password) throws Exception {
            return post("/auth/login",
                json.writeValueAsString(Map.of("correo", mail, "password", password)),
                "application/json", true);
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> patch(String path, String body, boolean csrf) throws Exception {
            return post(path, body, "application/json", csrf, "PATCH");
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return post(path, body, "application/json", true, method);
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
                builder.header(token.path("headerName").asString(), token.path("token").asString());
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
