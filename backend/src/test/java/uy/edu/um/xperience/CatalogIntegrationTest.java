package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** RF3 — catálogo público de cursos y proyectos publicados. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String CATALOG = "/catalog/courses";
    private static final String MANAGE = "/company/courses";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @Test
    void visitorCanAccessCatalog() throws Exception {
        assertThat(anonymous().get(CATALOG).statusCode()).isEqualTo(200);
    }

    @Test
    void catalogReturnsPublishedCourses() throws Exception {
        var admin = admin(company());
        UUID id = publishCourse(admin, "CURSO", "Catálogo Curso Java", "Java", "INICIAL", 10, "0");
        JsonNode items = items(anonymous().get(CATALOG + "?q=" + URLEncoder.encode("Catálogo Curso Java", StandardCharsets.UTF_8)));
        assertThat(items).anySatisfy(n -> {
            assertThat(n.path("id").asText()).isEqualTo(id.toString());
            assertThat(n.path("tipo").asText()).isEqualTo("CURSO");
            assertThat(n.path("titulo").asText()).isEqualTo("Catálogo Curso Java");
        });
    }

    @Test
    void catalogReturnsPublishedProjects() throws Exception {
        var admin = admin(company());
        UUID id = publishCourse(admin, "PROYECTO", "Catálogo Proyecto API", "Spring", "INTERMEDIO", 20, "100.00");
        JsonNode items = items(anonymous().get(CATALOG + "?q=" + URLEncoder.encode("Catálogo Proyecto API", StandardCharsets.UTF_8)));
        assertThat(items).anySatisfy(n -> {
            assertThat(n.path("id").asText()).isEqualTo(id.toString());
            assertThat(n.path("tipo").asText()).isEqualTo("PROYECTO");
        });
    }

    @Test
    void searchByNameFindsCourse() throws Exception {
        var admin = admin(company());
        String title = "BuscarCurso-" + UUID.randomUUID();
        publishCourse(admin, "CURSO", title, "Java", "INICIAL", 8, "0");
        JsonNode items = items(anonymous().get(CATALOG + "?q=" + enc(title.substring(0, 12))));
        assertThat(items).anyMatch(n -> title.equals(n.path("titulo").asText()));
    }

    @Test
    void searchByNameFindsProject() throws Exception {
        var admin = admin(company());
        String title = "BuscarProyecto-" + UUID.randomUUID();
        publishCourse(admin, "PROYECTO", title, "React", "AVANZADO", 30, "50");
        JsonNode items = items(anonymous().get(CATALOG + "?nombre=" + enc(title.substring(0, 14))));
        assertThat(items).anyMatch(n -> title.equals(n.path("titulo").asText()));
    }

    @Test
    void searchMissingReturnsEmpty() throws Exception {
        JsonNode items = items(anonymous().get(CATALOG + "?q=" + enc("zzz-inexistente-" + UUID.randomUUID())));
        assertThat(items).isEmpty();
    }

    @Test
    void filterByTechnology() throws Exception {
        var admin = admin(company());
        String tech = "Tech-" + UUID.randomUUID().toString().substring(0, 8);
        publishCourse(admin, "CURSO", "A " + tech, tech, "INICIAL", 5, "0");
        publishCourse(admin, "PROYECTO", "B other", "OtraTech", "INICIAL", 5, "0");
        JsonNode items = items(anonymous().get(CATALOG + "?tecnologia=" + enc(tech)));
        assertThat(items).isNotEmpty().allMatch(n -> tech.equalsIgnoreCase(n.path("tecnologia").asText()));
    }

    @Test
    void filterByLevel() throws Exception {
        var admin = admin(company());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        publishCourse(admin, "CURSO", "NivelAv-" + marker, "Java", "AVANZADO", 12, "10");
        publishCourse(admin, "CURSO", "NivelIn-" + marker, "Java", "INICIAL", 12, "10");
        JsonNode items = items(anonymous().get(CATALOG + "?nivel=AVANZADO&q=" + enc(marker)));
        assertThat(items).isNotEmpty().allMatch(n -> "AVANZADO".equals(n.path("nivel").asText()));
    }

    @Test
    void filterByDuration() throws Exception {
        var admin = admin(company());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        publishCourse(admin, "CURSO", "Dur40-" + marker, "Java", "INICIAL", 40, "0");
        publishCourse(admin, "CURSO", "Dur5-" + marker, "Java", "INICIAL", 5, "0");
        JsonNode items = items(anonymous().get(CATALOG + "?duracionMin=30&duracionMax=50&q=" + enc(marker)));
        assertThat(items).extracting(n -> n.path("titulo").asText()).containsExactly("Dur40-" + marker);
    }

    @Test
    void filterByCostIncludingZero() throws Exception {
        var admin = admin(company());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        publishCourse(admin, "CURSO", "Gratis-" + marker, "Java", "INICIAL", 10, "0");
        publishCourse(admin, "CURSO", "Pago-" + marker, "Java", "INICIAL", 10, "99.50");
        JsonNode free = items(anonymous().get(CATALOG + "?costoMin=0&costoMax=0&q=" + enc(marker)));
        assertThat(free).extracting(n -> n.path("titulo").asText()).containsExactly("Gratis-" + marker);
        JsonNode paid = items(anonymous().get(CATALOG + "?costoMin=50&costoMax=100&q=" + enc(marker)));
        assertThat(paid).extracting(n -> n.path("titulo").asText()).containsExactly("Pago-" + marker);
    }

    @Test
    void combinesTwoFilters() throws Exception {
        var admin = admin(company());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        publishCourse(admin, "CURSO", "Combo-" + marker, "Kotlin", "INTERMEDIO", 15, "20");
        publishCourse(admin, "CURSO", "Other-" + marker, "Kotlin", "INICIAL", 15, "20");
        JsonNode items = items(anonymous().get(CATALOG + "?tecnologia=Kotlin&nivel=INTERMEDIO&q=" + enc(marker)));
        assertThat(items).extracting(n -> n.path("titulo").asText()).containsExactly("Combo-" + marker);
    }

    @Test
    void combinesAllFilters() throws Exception {
        var admin = admin(company());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String title = "AllFilters-" + marker;
        publishCourse(admin, "PROYECTO", title, "Go", "AVANZADO", 25, "75.00");
        publishCourse(admin, "PROYECTO", "Near-" + marker, "Go", "AVANZADO", 25, "80.00");
        String query = "?q=" + enc("AllFilters") + "&tecnologia=Go&nivel=AVANZADO&duracionMin=20&duracionMax=30&costoMin=70&costoMax=76";
        JsonNode items = items(anonymous().get(CATALOG + query));
        assertThat(items).extracting(n -> n.path("titulo").asText()).containsExactly(title);
    }

    @Test
    void clearingFiltersReturnsFullCatalogSubset() throws Exception {
        var admin = admin(company());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        publishCourse(admin, "CURSO", "ClearA-" + marker, "Java", "INICIAL", 10, "0");
        publishCourse(admin, "PROYECTO", "ClearB-" + marker, "React", "INTERMEDIO", 20, "5");
        JsonNode filtered = items(anonymous().get(CATALOG + "?tecnologia=Java&q=" + enc(marker)));
        assertThat(filtered).hasSize(1);
        JsonNode all = items(anonymous().get(CATALOG + "?q=" + enc(marker)));
        assertThat(all).hasSize(2);
    }

    @Test
    void openCourseShowsDetail() throws Exception {
        var admin = admin(company());
        UUID id = publishCourse(admin, "CURSO", "Detalle Curso", "Java", "INICIAL", 10, "0");
        var response = anonymous().get(CATALOG + "/" + id);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("tipo").asText()).isEqualTo("CURSO");
        assertThat(body.path("titulo").asText()).isEqualTo("Detalle Curso");
        assertThat(body.path("descripcion").asText()).isEqualTo("Descripción");
        assertThat(body.path("empresaNombre").asText()).isNotBlank();
        assertThat(body.path("lecciones").isMissingNode() || body.path("lecciones").isNull()).isTrue();
    }

    @Test
    void openProjectShowsDetail() throws Exception {
        var admin = admin(company());
        UUID id = publishCourse(admin, "PROYECTO", "Detalle Proyecto", "SQL", "INTERMEDIO", 18, "12.50");
        var response = anonymous().get(CATALOG + "/" + id);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("tipo").asText()).isEqualTo("PROYECTO");
        assertThat(body.path("titulo").asText()).isEqualTo("Detalle Proyecto");
        assertThat(body.path("costo").decimalValue()).isEqualByComparingTo("12.50");
    }

    @Test
    void invalidTechnologyLengthRejected() throws Exception {
        String tooLong = "t".repeat(61);
        var response = anonymous().get(CATALOG + "?tecnologia=" + enc(tooLong));
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void invalidLevelRejected() throws Exception {
        var response = anonymous().get(CATALOG + "?nivel=EXPERTO");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).path("message").asText()).containsIgnoringCase("Nivel");
    }

    @Test
    void invalidCostRangeRejected() throws Exception {
        assertThat(anonymous().get(CATALOG + "?costoMin=50&costoMax=10").statusCode()).isEqualTo(400);
        assertThat(anonymous().get(CATALOG + "?costoMin=-1").statusCode()).isEqualTo(400);
    }

    @Test
    void invalidDurationRangeRejected() throws Exception {
        assertThat(anonymous().get(CATALOG + "?duracionMin=50&duracionMax=10").statusCode()).isEqualTo(400);
        assertThat(anonymous().get(CATALOG + "?duracionMin=0").statusCode()).isEqualTo(400);
        assertThat(anonymous().get(CATALOG + "?duracionMax=abc").statusCode()).isEqualTo(400);
    }

    @Test
    void excessivelyLongSearchRejected() throws Exception {
        String tooLong = "a".repeat(151);
        assertThat(anonymous().get(CATALOG + "?q=" + enc(tooLong)).statusCode()).isEqualTo(400);
    }

    @Test
    void draftAndUnpublishedAreHidden() throws Exception {
        var admin = admin(company());
        UUID draft = create(admin, "CURSO", "Borrador secreto");
        UUID published = publishCourse(admin, "CURSO", "Visible pub", "Java", "INICIAL", 10, "0");
        assertThat(anonymous().get(CATALOG + "/" + draft).statusCode()).isIn(401, 403);
        assertThat(anonymous().get(CATALOG + "/" + published).statusCode()).isEqualTo(200);
        JsonNode items = items(anonymous().get(CATALOG + "?q=" + enc("Borrador secreto")));
        assertThat(items).noneMatch(n -> "Borrador secreto".equals(n.path("titulo").asText()));
    }

    // ---- apoyo ----

    private UUID publishCourse(Browser admin, String type, String title, String tech, String level,
                               int hours, String cost) throws Exception {
        UUID id = create(admin, type, title, tech, level, hours, cost);
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
        return create(client, type, title, "Java", "INICIAL", 10, "0");
    }

    private UUID create(Browser client, String type, String title, String tech, String level,
                        int hours, String cost) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("tipo", type);
        body.put("titulo", title);
        body.put("descripcion", "Descripción");
        body.put("tecnologia", tech);
        body.put("nivel", level);
        body.put("duracionHoras", hours);
        body.put("costo", new java.math.BigDecimal(cost));
        var response = client.send("POST", MANAGE, json.writeValueAsString(body));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private JsonNode items(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("items");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private Browser login(String email) throws Exception {
        var client = new Browser();
        assertThat(client.login(email).statusCode()).isEqualTo(204);
        return client;
    }

    private Browser anonymous() {
        return new Browser();
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return send(method, path, HttpRequest.BodyPublishers.ofString(body), "application/json");
        }

        HttpResponse<String> login(String email) throws Exception {
            return send("POST", "/auth/login", HttpRequest.BodyPublishers.ofString(
                "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
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
