package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
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

/** RF6 de ofertas y sus límites de publicación, instancia, rol y campo sobre HTTP real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfferIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String COMPANY = "/company/offers";
    private static final String PUBLISHED = "/offers";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RECLUTADOR"})
    void authorizedStaffCreatesEditsAndPublishesWithCoursesFromAnotherCompany(String role) throws Exception {
        UUID company = company();
        var staff = member(company, role);
        UUID otherCompany = company();
        UUID course = course(otherCompany, "CURSO", "PUBLICADO");
        UUID project = course(otherCompany, "PROYECTO", "PUBLICADO");
        assertThat(staff.send("POST", COMPANY + "?empresaId=" + otherCompany,
            data("Manipulada", List.of(course, project))).statusCode()).isEqualTo(400);
        var created = staff.send("POST", COMPANY,
            data("  Desarrollador Java  ", List.of(course, project)));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        UUID id = UUID.fromString(body.path("id").asText());
        assertThat(body.path("titulo").asText()).isEqualTo("Desarrollador Java");
        assertThat(body.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(body.path("cursosRequeridos")).hasSize(2);
        assertThat(jdbc.queryForMap("SELECT empresa_id, creado_por FROM oferta WHERE id = ?", id))
            .containsEntry("empresa_id", company).containsEntry("creado_por", staff.id);
        assertThat(created.body()).doesNotContain("empresaId", "creadoPor", "creado_por");
        assertThat(json.readTree(staff.get(COMPANY).body())).hasSize(1);

        assertThat(staff.send("PUT", COMPANY + "/" + id, data("Backend Java", List.of(project))).statusCode())
            .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta_requisito WHERE oferta_id = ?", Integer.class, id))
            .isEqualTo(1);
        var published = staff.send("POST", COMPANY + "/" + id + "/publish", "{}");
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(json.readTree(published.body()).path("estado").asText()).isEqualTo("PUBLICADA");
        assertThat(staff.send("PUT", COMPANY + "/" + id, data("Cambio posterior", List.of())).statusCode())
            .isEqualTo(409);
        assertThat(staff.send("POST", COMPANY + "/" + id + "/publish", "").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT titulo FROM oferta WHERE id = ?", String.class, id))
            .isEqualTo("Backend Java");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "TALENTO"})
    void editorAndTalentCannotManageOffers(String role) throws Exception {
        UUID company = company();
        var admin = member(company, "ADMIN");
        UUID id = create(admin, "Oferta", List.of());
        var denied = "TALENTO".equals(role) ? talent() : member(company, role);
        List<HttpResponse<String>> responses = List.of(
            denied.get(COMPANY), denied.get(COMPANY + "/" + id),
            denied.send("POST", COMPANY, data("Otra", List.of())),
            denied.send("PUT", COMPANY + "/" + id, data("Cambio", List.of())),
            denied.send("POST", COMPANY + "/" + id + "/publish", ""));
        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(403));
        assertThat(jdbc.queryForMap("SELECT titulo, estado FROM oferta WHERE id = ?", id))
            .containsEntry("titulo", "Oferta").containsEntry("estado", "BORRADOR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta WHERE empresa_id = ?", Integer.class, company))
            .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RECLUTADOR", "EDITOR", "TALENTO"})
    void matrixRolesOnlySeePublishedOffersIncludingOtherCompanies(String role) throws Exception {
        var publisher = member(company(), "ADMIN");
        UUID draft = create(publisher, "Borrador", List.of());
        UUID published = create(publisher, "Publicada", List.of());
        assertThat(publisher.send("POST", COMPANY + "/" + published + "/publish", "").statusCode()).isEqualTo(200);
        var reader = "TALENTO".equals(role) ? talent() : member(company(), role);
        var list = reader.get(PUBLISHED);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json.readTree(list.body()))
            .allMatch(n -> "PUBLICADA".equals(n.path("estado").asText()))
            .anyMatch(n -> published.toString().equals(n.path("id").asText()))
            .noneMatch(n -> draft.toString().equals(n.path("id").asText()));
        assertThat(reader.get(PUBLISHED + "/" + published).statusCode()).isEqualTo(200);
        var hidden = reader.get(PUBLISHED + "/" + draft);
        var missing = reader.get(PUBLISHED + "/" + UUID.randomUUID());
        assertThat(hidden.statusCode()).isEqualTo(403);
        assertThat(hidden.body()).isEqualTo(missing.body());
    }

    @Test
    void rejectsEveryUnpublishedOrMissingRequirementWithoutPartialChanges() throws Exception {
        UUID company = company();
        var admin = member(company, "ADMIN");
        UUID valid = course(company, "CURSO", "PUBLICADO");
        UUID id = create(admin, "Original", List.of(valid));
        for (UUID invalid : List.of(course(company, "CURSO", "BORRADOR"),
                course(company, "PROYECTO", "BAJADO"), UUID.randomUUID())) {
            assertThat(admin.send("POST", COMPANY, data("No crear", List.of(valid, invalid))).statusCode())
                .isEqualTo(400);
            assertThat(admin.send("PUT", COMPANY + "/" + id, data("No editar", List.of(invalid))).statusCode())
                .isEqualTo(400);
            assertThat(jdbc.queryForObject("SELECT titulo FROM oferta WHERE id = ?", String.class, id))
                .isEqualTo("Original");
            assertThat(jdbc.queryForList("SELECT curso_id FROM oferta_requisito WHERE oferta_id = ?", UUID.class, id))
                .containsExactly(valid);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta WHERE empresa_id = ?", Integer.class, company))
            .isEqualTo(1);
    }

    @Test
    void publishRevalidatesRequirementsAndAValidDraftCanHaveNoRequirements() throws Exception {
        UUID company = company();
        var admin = member(company, "ADMIN");
        UUID requirement = course(company, "CURSO", "PUBLICADO");
        UUID id = create(admin, "Revalidar", List.of(requirement));
        jdbc.update("UPDATE curso SET estado = 'BAJADO' WHERE id = ?", requirement);
        assertThat(admin.send("POST", COMPANY + "/" + id + "/publish", "").statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT estado FROM oferta WHERE id = ?", String.class, id))
            .isEqualTo("BORRADOR");
        assertThat(admin.send("PUT", COMPANY + "/" + id, data("Sin requisitos", List.of())).statusCode())
            .isEqualTo(200);
        assertThat(admin.send("POST", COMPANY + "/" + id + "/publish", "").statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsForbiddenFieldsOnCreateUpdateAndPublishWithoutChangingAnything() throws Exception {
        UUID company = company();
        var admin = member(company, "ADMIN");
        UUID id = create(admin, "Original", List.of());
        for (String field : List.of("id", "empresaId", "usuarioId", "creadoPor", "estado", "creadoEn", "actualizadoEn")) {
            var manipulated = new HashMap<String, Object>();
            manipulated.put("titulo", "Manipulada");
            manipulated.put("descripcion", "Cambio");
            manipulated.put("cursosRequeridos", List.of());
            manipulated.put(field, UUID.randomUUID().toString());
            String input = json.writeValueAsString(manipulated);
            assertThat(admin.send("POST", COMPANY, input).statusCode()).as(field).isEqualTo(400);
            assertThat(admin.send("PUT", COMPANY + "/" + id, input).statusCode()).as(field).isEqualTo(400);
        }
        assertThat(admin.send("POST", COMPANY + "/" + id + "/publish", "{\"estado\":\"PUBLICADA\"}")
            .statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForMap("SELECT titulo, estado FROM oferta WHERE id = ?", id))
            .containsEntry("titulo", "Original").containsEntry("estado", "BORRADOR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta WHERE empresa_id = ?", Integer.class, company))
            .isEqualTo(1);
    }

    @Test
    void enforcesInputBoundsNullAndUniqueRequirements() throws Exception {
        UUID company = company();
        var admin = member(company, "ADMIN");
        UUID requirement = course(company, "CURSO", "PUBLICADO");
        assertThat(admin.send("POST", COMPANY, data("Repetidos", List.of(requirement, requirement))).statusCode())
            .isEqualTo(400);
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(UUID.randomUUID());
        assertThat(admin.send("POST", COMPANY, data("Demasiados", many)).statusCode()).isEqualTo(400);
        for (String body : List.of(data(" ", List.of()), data("x".repeat(151), List.of()),
                "{\"titulo\":\"Oferta\",\"descripcion\":\"" + "x".repeat(4001) + "\",\"cursosRequeridos\":[]}",
                "{\"titulo\":\"Oferta\",\"descripcion\":\"Desc\",\"cursosRequeridos\":[null]}",
                "{\"titulo\":\"Oferta\",\"descripcion\":\"Desc\"}")) {
            assertThat(admin.send("POST", COMPANY, body).statusCode()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta WHERE empresa_id = ?", Integer.class, company))
            .isZero();
    }

    @Test
    void companyScopeCannotBeOverriddenAndForeignOfferLooksMissing() throws Exception {
        UUID companyA = company();
        var adminA = member(companyA, "ADMIN");
        UUID idA = create(adminA, "Empresa A", List.of());
        UUID companyB = company();
        var recruiterB = member(companyB, "RECLUTADOR");
        UUID idB = create(recruiterB, "Empresa B", List.of());
        assertThat(recruiterB.get(COMPANY + "?empresaId=" + companyA).statusCode()).isEqualTo(400);
        var ownList = recruiterB.get(COMPANY);
        assertThat(json.readTree(ownList.body())).hasSize(1);
        assertThat(json.readTree(ownList.body()).get(0).path("id").asText()).isEqualTo(idB.toString());
        var foreign = recruiterB.get(COMPANY + "/" + idA);
        var missing = recruiterB.get(COMPANY + "/" + UUID.randomUUID());
        assertThat(foreign.statusCode()).isEqualTo(403);
        assertThat(foreign.body()).isEqualTo(missing.body());
        assertThat(recruiterB.send("PUT", COMPANY + "/" + idA + "?empresaId=" + companyA,
            data("Cambio", List.of())).statusCode()).isEqualTo(400);
        assertThat(recruiterB.send("PUT", COMPANY + "/" + idA, data("Cambio", List.of())).statusCode()).isEqualTo(403);
        assertThat(recruiterB.send("POST", COMPANY + "/" + idA + "/publish", "").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForMap("SELECT titulo, estado FROM oferta WHERE id = ?", idA))
            .containsEntry("titulo", "Empresa A").containsEntry("estado", "BORRADOR");
    }

    @Test
    void simultaneousPublishIsSerializedAndOnlyOneChangesTheDraft() throws Exception {
        UUID company = company();
        var admin = member(company, "ADMIN");
        var recruiter = member(company, "RECLUTADOR");
        UUID id = create(admin, "Concurrente", List.of());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> admin.send("POST", COMPANY + "/" + id + "/publish", "").statusCode());
            var second = executor.submit(() -> recruiter.send("POST", COMPANY + "/" + id + "/publish", "").statusCode());
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM oferta WHERE id = ?", String.class, id)).isEqualTo("PUBLICADA");
    }

    @Test
    void authenticationCsrfAndSessionRoleChangesApplyWithoutBreakingThePublicCatalog() throws Exception {
        var visitor = new Browser();
        assertThat(visitor.get(PUBLISHED).statusCode()).isEqualTo(401);
        assertThat(visitor.get(COMPANY).statusCode()).isEqualTo(401);
        assertThat(visitor.send("POST", COMPANY, data("Anonima", List.of())).statusCode()).isEqualTo(401);
        UUID company = company();
        UUID requirement = course(company, "CURSO", "PUBLICADO");
        var admin = member(company, "ADMIN");
        var withoutToken = admin.client.send(HttpRequest.newBuilder(uri(COMPANY))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(data("Sin CSRF", List.of())))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(withoutToken.statusCode()).isEqualTo(403);
        create(admin, "Con requisito", List.of(requirement));
        var catalog = visitor.get("/catalog/courses/" + requirement);
        assertThat(catalog.statusCode()).isEqualTo(200);
        assertThat(json.readTree(catalog.body()).path("id").asText()).isEqualTo(requirement.toString());
        assertThat(catalog.body()).doesNotContain("oferta", "postulacion", "creadoPor");
        jdbc.update("UPDATE membresia SET rol = 'EDITOR' WHERE usuario_id = ?", admin.id);
        assertThat(admin.get(COMPANY).statusCode()).isEqualTo(403);
        assertThat(admin.get(PUBLISHED).statusCode()).isEqualTo(200);
    }

    private UUID company() {
        return provisioner.create("Empresa " + UUID.randomUUID(),
            new RegisterRequest(email(), "Admin", "Prueba", PASSWORD), "test-suite");
    }

    private Browser member(UUID company, String role) throws Exception {
        String email = email();
        UUID id = accounts.create(email, "Staff", "Prueba", passwords.encode(PASSWORD), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')", id, company, role);
        var browser = new Browser();
        assertThat(browser.login(email).statusCode()).isEqualTo(204);
        return browser;
    }

    private Browser talent() throws Exception {
        String email = email();
        var browser = new Browser();
        assertThat(browser.send("POST", "/auth/register",
            json.writeValueAsString(new RegisterRequest(email, "Ana", "Prueba", PASSWORD))).statusCode()).isEqualTo(202);
        assertThat(browser.login(email).statusCode()).isEqualTo(204);
        return browser;
    }

    private UUID course(UUID company, String type, String state) {
        UUID id = UUID.randomUUID();
        UUID author = jdbc.queryForObject("SELECT usuario_id FROM membresia WHERE empresa_id = ? AND rol = 'ADMIN' ORDER BY usuario_id LIMIT 1",
            UUID.class, company);
        jdbc.update("""
            INSERT INTO curso (id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel,
                               duracion_horas, costo, estado, creado_por)
            VALUES (?, ?, ?, ?, 'Descripcion', 'Java', 'INICIAL', 10, 0, ?, ?)
            """, id, company, type, "Requisito " + id, state, author);
        return id;
    }

    private UUID create(Browser client, String title, List<UUID> requirements) throws Exception {
        var response = client.send("POST", COMPANY, data(title, requirements));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private String data(String title, List<UUID> requirements) throws Exception {
        return json.writeValueAsString(Map.of("titulo", title, "descripcion", "Descripcion de la oferta",
            "cursosRequeridos", requirements));
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private class Browser {
        final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        UUID id;

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return send(method, path, body, "application/json");
        }

        HttpResponse<String> login(String email) throws Exception {
            var response = send("POST", "/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
            id = accounts.byEmail(email).map(a -> a.id()).orElse(null);
            return response;
        }

        private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
            JsonNode token = json.readTree(get("/auth/csrf").body());
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType)
                .header(token.path("headerName").asText(), token.path("token").asText())
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
