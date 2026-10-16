package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.RequiereAccion;
import static org.assertj.core.api.Assertions.*;

/**
 * RS14 — Los archivos (material de cursos y currículums) siguen las mismas reglas de autorización que
 * el resto de los recursos. Sólo se entregan por endpoints del backend que atraviesan el PEP; el
 * almacenamiento no es alcanzable desde el navegador y su clave nunca funciona como llave de acceso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Rs14FileAuthorizationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String COURSES = "/company/courses";
    private static final String OFFERS = "/company/offers";
    private static final Path STORAGE = Path.of("./target/test-archivos");
    /** Endpoints que entregan archivos y la acción que exigen. Uno nuevo (certificados, imágenes) se revisa acá. */
    private static final Map<String, String> FILE_ENDPOINTS = Map.of(
        "CourseController#downloadMaterial", "material.descargar",
        "CurriculumController#download", "curriculum.descargar",
        "CompanyApplicationController#curriculum", "curriculum.descargar");

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebProperties web;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping;

    // ---- Misma autorización que el recurso ----

    @Test
    void courseMaterialFollowsTheCourseAuthorization() throws Exception {
        var f = fixture();
        String path = COURSES + "/" + f.course + "/materials/" + f.material;
        String missing = COURSES + "/" + f.course + "/materials/" + UUID.randomUUID();

        for (var allowed : List.of(f.editorA, f.adminA, f.enrolled)) {
            var response = allowed.bytes(path);
            assertThat(response.statusCode()).as(allowed.email).isEqualTo(200);
            assertThat(response.body()).isEqualTo(f.materialBytes);
        }
        String missingBody = f.editorB.get(missing).body();
        for (var denied : List.of(f.recruiterA, f.editorB, f.adminB, f.applicant, f.stranger)) {
            var response = denied.get(path);
            assertThat(response.statusCode()).as(denied.email).isEqualTo(403);
            assertThat(response.body()).as(denied.email).isEqualTo(missingBody).doesNotContain("%PDF");
        }
        assertThat(new Browser(null).get(path).statusCode()).isEqualTo(401);
    }

    @Test
    void curriculumFollowsOwnershipAndTheApplicationRelation() throws Exception {
        var f = fixture();
        String own = "/profile/me/curriculum";
        String company = OFFERS + "/" + f.offer + "/applications/" + f.application + "/curriculum";
        String missing = OFFERS + "/" + f.offer + "/applications/" + UUID.randomUUID() + "/curriculum";

        assertThat(f.applicant.bytes(own).body()).isEqualTo(f.cvBytes);
        // Otro Talento sólo alcanza el suyo: la ruta no recibe identificador.
        assertThat(f.stranger.bytes(own).body()).isNotEqualTo(f.cvBytes);
        for (var staff : List.of(f.editorA, f.adminA, f.recruiterA)) {
            assertThat(staff.get(own).statusCode()).as(staff.email).isEqualTo(403);
        }

        for (var allowed : List.of(f.adminA, f.recruiterA)) {
            var response = allowed.bytes(company);
            assertThat(response.statusCode()).as(allowed.email).isEqualTo(200);
            assertThat(response.body()).isEqualTo(f.cvBytes);
        }
        String missingBody = f.adminB.get(missing).body();
        for (var denied : List.of(f.editorA, f.adminB, f.editorB, f.applicant, f.enrolled)) {
            var response = denied.get(company);
            assertThat(response.statusCode()).as(denied.email).isEqualTo(403);
            assertThat(response.body()).as(denied.email).isEqualTo(missingBody).doesNotContain("%PDF");
        }
        var visitor = new Browser(null);
        assertThat(visitor.get(own).statusCode()).isEqualTo(401);
        assertThat(visitor.get(company).statusCode()).isEqualTo(401);
    }

    // ---- El almacenamiento no es alcanzable ----

    @Test
    void storageKeysAreOpaqueAndNeverWorkAsAnAccessKey() throws Exception {
        var f = fixture();
        String materialKey = jdbc.queryForObject("SELECT clave_almacen FROM material WHERE id = ?", String.class, f.material);
        String cvKey = jdbc.queryForObject("SELECT clave_almacen FROM curriculum WHERE usuario_id = ?", String.class,
            f.applicant.id);

        for (String key : List.of(materialKey, cvKey)) {
            // En disco: UUID canónico, sin extensión ni nombre del cliente, dentro del directorio privado.
            assertThat(UUID.fromString(key).toString()).isEqualTo(key);
            assertThat(STORAGE.resolve(key)).isRegularFile();
            // La clave nunca sale en las respuestas, así que no hay URL directa ni enlace firmado que compartir.
            assertThat(f.editorA.get(COURSES + "/" + f.course).body()).doesNotContain(key);
            assertThat(f.applicant.get("/profile/me/curriculum/metadata").body()).doesNotContain(key);
            assertThat(f.adminA.get(OFFERS + "/" + f.offer + "/applications").body()).doesNotContain(key);
        }

        // Ni siquiera el dueño llega al archivo pidiendo su ubicación por fuera de los endpoints.
        for (var client : List.of(f.editorA, f.applicant, new Browser(null))) {
            for (String key : List.of(materialKey, cvKey)) {
                for (String path : List.of("/" + key, "/archivos/" + key, "/test-archivos/" + key,
                        "/target/test-archivos/" + key, "/.data/archivos/" + key, "/static/" + key,
                        "/api/" + key, "/api/archivos/" + key, "/api/files/" + key, "/api/storage/" + key,
                        "/api/%2e%2e/target/test-archivos/" + key,
                        "/api" + COURSES + "/" + f.course + "/materials/..%2F..%2F" + key)) {
                    var response = client.raw(path);
                    assertThat(response.statusCode()).as(path).isIn(400, 401, 403, 404);
                    assertThat(response.body()).as(path).isNotEqualTo(f.materialBytes).isNotEqualTo(f.cvBytes);
                }
            }
        }

        // Usada como identificador de material o de postulación, la clave es un UUID más: se deniega igual.
        String unknown = f.editorA.get(COURSES + "/" + f.course + "/materials/" + UUID.randomUUID()).body();
        var asMaterial = f.editorA.get(COURSES + "/" + f.course + "/materials/" + materialKey);
        assertThat(asMaterial.statusCode()).isEqualTo(403);
        assertThat(asMaterial.body()).isEqualTo(unknown);
        assertThat(f.adminA.get(OFFERS + "/" + f.offer + "/applications/" + cvKey + "/curriculum").statusCode())
            .isEqualTo(403);
    }

    @Test
    void backendServesNoStaticResources() {
        assertThat(web.getResources().isAddMappings()).isFalse();
    }

    // ---- Cómo se entrega ----

    @Test
    void downloadsUseTheServerDetectedTypeAndNameAndAreNeverRendered() throws Exception {
        var f = fixture();
        // Un PDF subido como HTML: el nombre servido conserva el texto pero termina en la extensión real.
        String upload = f.editorA.upload(COURSES + "/" + f.course + "/materials", "pagina.html", "text/html", f.materialBytes);
        String material = json.readTree(upload).path("materiales").findValuesAsText("id").stream()
            .filter(id -> !id.equals(f.material.toString())).findFirst().orElseThrow();
        f.applicant.upload("/profile/me/curriculum", "cv.html", "text/html", f.cvBytes);

        var downloads = Map.of(
            "material", f.editorA.bytes(COURSES + "/" + f.course + "/materials/" + material),
            "cv propio", f.applicant.bytes("/profile/me/curriculum"),
            "cv de postulación", f.adminA.bytes(OFFERS + "/" + f.offer + "/applications/" + f.application + "/curriculum"));
        downloads.forEach((name, response) -> {
            assertThat(response.statusCode()).as(name).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).as(name).hasValue("application/pdf");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).as(name).hasValue("nosniff");
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).as(name).contains("no-store");
            var disposition = ContentDisposition.parse(response.headers().firstValue("Content-Disposition").orElseThrow());
            assertThat(disposition.isAttachment()).as(name).isTrue();
            assertThat(disposition.getFilename()).as(name).endsWith(".pdf").doesNotContain("/", "\\", "\"");
        });
        assertThat(ContentDisposition.parse(downloads.get("material").headers().firstValue("Content-Disposition")
            .orElseThrow()).getFilename()).isEqualTo("pagina.html.pdf");
    }

    @Test
    void everyEndpointThatReturnsAFileIsReviewedAndDeclaresAFileAction() {
        var found = new HashMap<String, String>();
        for (HandlerMethod handler : mapping.getHandlerMethods().values()) {
            var type = ResolvableType.forMethodReturnType(handler.getMethod());
            if (ResponseEntity.class.equals(type.resolve())) type = type.getGeneric(0);
            Class<?> body = type.resolve(Object.class);
            boolean file = Resource.class.isAssignableFrom(body) || byte[].class.equals(body)
                || InputStream.class.isAssignableFrom(body) || StreamingResponseBody.class.isAssignableFrom(body);
            if (!file) continue;
            var action = handler.getMethodAnnotation(RequiereAccion.class);
            found.put(handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName(),
                action == null ? "SIN ACCION" : action.value());
        }
        assertThat(found).as("endpoints que entregan archivos").isEqualTo(FILE_ENDPOINTS);
    }

    // ---- Apoyo ----

    private record Fixture(UUID course, UUID material, byte[] materialBytes, UUID offer, UUID application,
                           byte[] cvBytes, Browser adminA, Browser editorA, Browser recruiterA, Browser adminB,
                           Browser editorB, Browser applicant, Browser enrolled, Browser stranger) {}

    private Fixture fixture() throws Exception {
        UUID companyA = company(), companyB = company();
        var adminA = admin(companyA);
        var editorA = member(companyA, "EDITOR");
        var recruiterA = member(companyA, "RECLUTADOR");
        var adminB = admin(companyB);
        var editorB = member(companyB, "EDITOR");

        byte[] materialBytes = ("%PDF-1.4\nMaterial RS14 " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
        UUID course = createCourse(editorA);
        assertThat(editorA.send("POST", COURSES + "/" + course + "/lessons",
            json.writeValueAsString(Map.of("titulo", "L1", "cuerpo", "Cuerpo"))).statusCode()).isEqualTo(200);
        UUID material = UUID.fromString(json.readTree(editorA.upload(COURSES + "/" + course + "/materials", "guia.pdf",
            "application/pdf", materialBytes)).path("materiales").get(0).path("id").asText());
        assertThat(editorA.send("POST", COURSES + "/" + course + "/publish", "").statusCode()).isEqualTo(200);

        var enrolled = registerTalent();
        assertThat(enrolled.send("POST", "/enrollments", json.writeValueAsString(Map.of("cursoId", course.toString())))
            .statusCode()).isEqualTo(201);
        var stranger = registerTalent();

        var created = adminA.send("POST", OFFERS, json.writeValueAsString(
            Map.of("titulo", "Oferta RS14", "descripcion", "Desc", "cursosRequeridos", List.of())));
        UUID offer = UUID.fromString(json.readTree(created.body()).path("id").asText());
        assertThat(adminA.send("POST", OFFERS + "/" + offer + "/publish", "").statusCode()).isEqualTo(200);

        byte[] cvBytes = ("%PDF-1.4\nCV RS14 " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
        var applicant = registerTalent();
        applicant.upload("/profile/me/curriculum", "cv.pdf", "application/pdf", cvBytes);
        var applied = applicant.send("POST", "/offers/" + offer + "/applications", "{}");
        assertThat(applied.statusCode()).isEqualTo(201);
        UUID application = UUID.fromString(json.readTree(applied.body()).path("id").asText());

        return new Fixture(course, material, materialBytes, offer, application, cvBytes, adminA, editorA, recruiterA,
            adminB, editorB, applicant, enrolled, stranger);
    }

    private UUID createCourse(Browser editor) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("tipo", "CURSO");
        body.put("titulo", "Curso RS14");
        body.put("descripcion", "Descripción");
        body.put("tecnologia", "Java");
        body.put("nivel", "INICIAL");
        body.put("duracionHoras", 10);
        body.put("costo", 0);
        var response = editor.send("POST", COURSES, json.writeValueAsString(body));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private Browser registerTalent() throws Exception {
        String email = email();
        var client = new Browser(email);
        assertThat(client.send("POST", "/auth/register", json.writeValueAsString(
            new RegisterRequest(email, "Ana", "Prueba", PASSWORD))).statusCode()).isEqualTo(202);
        assertThat(client.login().statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
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
        var client = new Browser(email);
        assertThat(client.login().statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
    }

    private Browser member(UUID company, String role) throws Exception {
        String email = email();
        UUID id = accounts.create(email, "Staff", "Prueba", passwords.encode(PASSWORD), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')",
            id, company, role);
        var client = new Browser(email);
        assertThat(client.login().statusCode()).isEqualTo(204);
        client.id = id;
        return client;
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String email;
        UUID id;

        Browser(String email) { this.email = email; }

        HttpResponse<String> login() throws Exception {
            return send("POST", "/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded");
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(api(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<byte[]> bytes(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(api(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        /** Ruta tal cual, sin el prefijo /api ni normalización. */
        HttpResponse<byte[]> raw(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return send(method, path, body, "application/json");
        }

        HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
            return send(method, path, HttpRequest.BodyPublishers.ofString(body), contentType);
        }

        String upload(String path, String fileName, String partType, byte[] content) throws Exception {
            String boundary = "rs14" + UUID.randomUUID();
            var out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + partType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var response = send("POST", path, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()),
                "multipart/form-data; boundary=" + boundary);
            assertThat(response.statusCode()).as(path).isIn(200, 201);
            return response.body();
        }

        private HttpResponse<String> send(String method, String path, HttpRequest.BodyPublisher body, String contentType)
                throws Exception {
            var token = json.readTree(get("/auth/csrf").body());
            var request = HttpRequest.newBuilder(api(path)).header("Content-Type", contentType)
                .header(token.path("headerName").asText(), token.path("token").asText())
                .method(method, body).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private URI api(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    }
}
