package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.Sujeto;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** RF6: endpoints reales a través de la cadena Security, política vigente y persistencia H2. */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final byte[] ORIGINAL = "%PDF-1.4\nCV original\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] REPLACEMENT = "%PDF-1.7\nCV reemplazo\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired RegistrationService registration;
    @Autowired CompanyProvisioner provisioner;
    private record Company(UUID id, UUID admin) {}

    @Test void persistedApplicationKeepsOriginalCvAndProjectsOnlyAllowedProfileFields() throws Exception {
        Company company = company();
        UUID offer = offer(company, true);
        UUID talent = talent("Ana", List.of("Backend", "React"));
        UUID original = upload(talent, ORIGINAL);
        JsonNode application = apply(talent, offer);
        UUID id = UUID.fromString(application.path("id").asText());
        assertThat(application.size()).isEqualTo(6);
        assertThat(application.path("estado").asText()).isEqualTo("ENVIADA");
        assertThat(jdbc.queryForObject("SELECT curriculum_id FROM postulacion WHERE id = ?", UUID.class, id))
            .isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM postulacion WHERE id = ?", UUID.class, id))
            .isEqualTo(company.id());
        UUID replacement = upload(talent, REPLACEMENT);
        assertThat(replacement).isNotEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT curriculum_id FROM postulacion WHERE id = ?", UUID.class, id))
            .isEqualTo(original);
        assertThat(getAs(talent, "/api/profile/me/curriculum").getResponse().getContentAsByteArray())
            .isEqualTo(REPLACEMENT);
        MvcResult download = getAs(company.admin(), nested(offer, id, "curriculum"));
        assertThat(download.getResponse().getStatus()).isEqualTo(200);
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(ORIGINAL);
        assertThat(download.getResponse().getHeader("Cache-Control")).contains("no-store");
        JsonNode list = tree(getAs(company.admin(), listing(offer)));
        assertThat(list).hasSize(1);
        assertSafeProfile(list.get(0).path("perfil"));
        assertThat(list.get(0).path("perfil").path("especializaciones")).hasSize(2);
        assertSafeProfile(tree(getAs(company.admin(), nested(offer, id, "profile"))));
        assertThat(list.toString()).doesNotContain("telefono", "notificar", "password", "token", "claveAlmacen",
            "ubicacion", "curriculumId", "talentoId", "empresaId", "permisos", "otrasPostulaciones");
        assertThat(tree(getAs(talent, "/api/applications/me"))).hasSize(1);
    }

    @Test void duplicateAndConcurrentSubmissionsCreateExactlyOneApplication() throws Exception {
        Company company = company(); UUID offer = offer(company, true); UUID talent = talent("Bruno", List.of());
        upload(talent, ORIGINAL);
        var first = CompletableFuture.supplyAsync(() -> applyStatus(talent, offer));
        var second = CompletableFuture.supplyAsync(() -> applyStatus(talent, offer));
        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 409);
        assertThat(applyStatus(talent, offer)).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion WHERE oferta_id = ? AND talento_id = ?",
            Integer.class, offer, talent)).isEqualTo(1);
    }

    @Test void concurrentReplacementUsesOneCompletePersistedCvVersion() throws Exception {
        Company company = company(); UUID offer = offer(company, true); UUID talent = talent("Carla", List.of());
        UUID original = upload(talent, ORIGINAL);
        var applying = CompletableFuture.supplyAsync(() -> applyStatus(talent, offer));
        var replacing = CompletableFuture.supplyAsync(() -> {
            try { return upload(talent, REPLACEMENT); } catch (Exception error) { throw new RuntimeException(error); }
        });
        assertThat(applying.join()).isEqualTo(201);
        UUID replacement = replacing.join();
        UUID snapshot = jdbc.queryForObject("SELECT curriculum_id FROM postulacion WHERE oferta_id = ? AND talento_id = ?",
            UUID.class, offer, talent);
        assertThat(snapshot).isIn(original, replacement);
        UUID id = jdbc.queryForObject("SELECT id FROM postulacion WHERE oferta_id = ? AND talento_id = ?", UUID.class, offer, talent);
        assertThat(getAs(company.admin(), nested(offer, id, "curriculum")).getResponse().getContentAsByteArray())
            .isEqualTo(snapshot.equals(original) ? ORIGINAL : REPLACEMENT);
    }

    @Test void missingCvDraftAndConcurrentPublicationAreHandledWithoutPartialRows() throws Exception {
        Company company = company(); UUID offer = offer(company, false); UUID talent = talent("Diego", List.of());
        assertThat(applyStatus(talent, offer)).isEqualTo(403);
        assertThat(send(company.admin(), "POST", "/api/company/offers/" + offer + "/publish", "{}").getResponse().getStatus())
            .isEqualTo(200);
        assertThat(applyStatus(talent, offer)).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion WHERE oferta_id = ?", Integer.class, offer)).isZero();
        upload(talent, ORIGINAL);
        UUID concurrent = offer(company, false);
        var publishing = CompletableFuture.supplyAsync(() -> {
            try { return send(company.admin(), "POST", "/api/company/offers/" + concurrent + "/publish", "{}")
                .getResponse().getStatus(); } catch (Exception error) { throw new RuntimeException(error); }
        });
        var applying = CompletableFuture.supplyAsync(() -> applyStatus(talent, concurrent));
        assertThat(publishing.join()).isEqualTo(200);
        assertThat(applying.join()).isIn(201, 403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion p JOIN oferta o ON o.id = p.oferta_id "
            + "WHERE p.oferta_id = ? AND o.estado <> 'PUBLICADA'", Integer.class, concurrent)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarioId", "talentoId", "empresaId", "estado", "fecha", "curriculumId", "id", "rol"})
    void manipulatedFieldsRejectEntireApplication(String field) throws Exception {
        Company company = company(); UUID offer = offer(company, true); UUID talent = talent("Elena", List.of());
        UUID current = upload(talent, ORIGINAL);
        var body = json.createObjectNode().put(field, "valor protegido");
        assertThat(send(talent, "POST", "/api/offers/" + offer + "/applications", body.toString())
            .getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion WHERE oferta_id = ?", Integer.class, offer)).isZero();
        assertThat(jdbc.queryForObject("SELECT curriculum_actual_id FROM perfil_talento WHERE usuario_id = ?", UUID.class, talent))
            .isEqualTo(current);
        assertThat(send(talent, "POST", "/api/offers/" + offer + "/applications?" + field + "=otro", "{}")
            .getResponse().getStatus()).isEqualTo(400);
    }

    @Test void companyAndNestedScopeDenyUnknownAndForeignResourcesIdentically() throws Exception {
        Company a = company(); Company b = company(); UUID offer = offer(a, true); UUID otherOwnOffer = offer(a, true);
        UUID talent = talent("Fernanda", List.of("Backend")); upload(talent, ORIGINAL);
        UUID application = UUID.fromString(apply(talent, offer).path("id").asText());
        for (String suffix : List.of("profile", "curriculum")) {
            MvcResult foreign = getAs(b.admin(), nested(offer, application, suffix));
            MvcResult unknown = getAs(b.admin(), nested(UUID.randomUUID(), UUID.randomUUID(), suffix));
            assertThat(foreign.getResponse().getStatus()).isEqualTo(403);
            assertThat(foreign.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
            assertThat(getAs(a.admin(), nested(otherOwnOffer, application, suffix)).getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(getAs(b.admin(), listing(offer)).getResponse().getStatus()).isEqualTo(403);
        assertThat(getAs(b.admin(), listing(offer)).getResponse().getContentAsString())
            .isEqualTo(getAs(b.admin(), listing(UUID.randomUUID())).getResponse().getContentAsString());
        assertThat(getAs(a.admin(), listing(offer) + "?empresaId=" + b.id()).getResponse().getStatus()).isEqualTo(400);
    }

    @Test void ownerOnlyListingsAuthenticationCsrfAndRolesAreEnforced() throws Exception {
        Company company = company(); UUID offer = offer(company, true);
        UUID first = talent("Gabriela", List.of()); UUID second = talent("Hector", List.of());
        upload(first, ORIGINAL); upload(second, ORIGINAL);
        UUID firstId = UUID.fromString(apply(first, offer).path("id").asText());
        UUID secondId = UUID.fromString(apply(second, offer).path("id").asText());
        assertThat(tree(getAs(first, "/api/applications/me")).get(0).path("id").asText()).isEqualTo(firstId.toString());
        assertThat(tree(getAs(second, "/api/applications/me")).get(0).path("id").asText()).isEqualTo(secondId.toString());
        assertThat(getAs(first, "/api/applications/me?talentoId=" + second).getResponse().getStatus()).isEqualTo(400);
        assertThat(getAs(first, listing(offer)).getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/applications/me")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/offers/" + offer + "/applications").with(csrf().asHeader()).contentType("application/json").content("{}"))
            .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/offers/" + offer + "/applications").with(user(first.toString()))
            .contentType("application/json").content("{}" )).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(applyStatus(company.admin(), offer)).isEqualTo(403);
        jdbc.update("UPDATE membresia SET rol = 'RECLUTADOR' WHERE usuario_id = ?", company.admin());
        assertThat(getAs(company.admin(), listing(offer)).getResponse().getStatus()).isEqualTo(200);
        jdbc.update("UPDATE membresia SET rol = 'EDITOR' WHERE usuario_id = ?", company.admin());
        assertThat(getAs(company.admin(), listing(offer)).getResponse().getStatus()).isEqualTo(403);
        assertThat(getAs(company.admin(), nested(offer, firstId, "profile")).getResponse().getStatus()).isEqualTo(403);
        assertThat(getAs(company.admin(), nested(offer, firstId, "curriculum")).getResponse().getStatus()).isEqualTo(403);
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", first);
        assertThat(getAs(first, "/api/applications/me").getResponse().getStatus()).isEqualTo(403);
    }

    @Test void filtersCombineWithinCompanyOfferAndUseExactSpecializationAndInclusiveUtcDates() throws Exception {
        Company company = company(); UUID offer = offer(company, true);
        UUID ana = talent("Ana", List.of("Backend")); UUID bruno = talent("Bruno", List.of("Frontend"));
        UUID carla = talent("Carla", List.of("Full Stack"));
        for (UUID talent : List.of(ana, bruno, carla)) { upload(talent, ORIGINAL); apply(talent, offer); }
        jdbc.update("UPDATE postulacion SET fecha = TIMESTAMP WITH TIME ZONE '2026-10-05 23:59:59+00' WHERE oferta_id = ? AND talento_id = ?", offer, bruno);
        jdbc.update("UPDATE postulacion SET fecha = TIMESTAMP WITH TIME ZONE '2026-10-06 23:59:59+00' WHERE oferta_id = ? AND talento_id = ?", offer, ana);
        jdbc.update("UPDATE postulacion SET fecha = TIMESTAMP WITH TIME ZONE '2026-10-07 00:00:00+00' WHERE oferta_id = ? AND talento_id = ?", offer, carla);
        Company another = company(); UUID anotherOffer = offer(another, true);
        UUID anotherAna = talent("Ana", List.of("Backend")); upload(anotherAna, ORIGINAL); apply(anotherAna, anotherOffer);
        assertThat(tree(getAs(company.admin(), listing(offer)))).hasSize(3);
        assertThat(tree(getAs(company.admin(), listing(offer) + "?q=ANa%20prueba"))).hasSize(1);
        assertThat(tree(getAs(company.admin(), listing(offer) + "?especializacion=Backend"))).hasSize(1);
        assertThat(tree(getAs(company.admin(), listing(offer) + "?especializacion=Full%20Stack"))).hasSize(1);
        assertThat(tree(getAs(company.admin(), listing(offer) + "?fechaDesde=2026-10-06&fechaHasta=2026-10-06"))).hasSize(1);
        assertThat(tree(getAs(company.admin(), listing(offer) + "?q=ana&especializacion=Backend&fechaDesde=2026-10-06&fechaHasta=2026-10-06")))
            .hasSize(1);
        assertThat(tree(getAs(company.admin(), listing(offer) + "?q=bruno&especializacion=Backend"))).isEmpty();
        assertThat(tree(getAs(company.admin(), listing(offer) + "?q=%25"))).isEmpty();
        assertThat(tree(getAs(company.admin(), listing(offer) + "?q=%27%20OR%201%3D1%20--"))).isEmpty();
        for (String query : List.of("especializacion=Back", "fechaDesde=2026-02-30", "fechaDesde=2026-10-07&fechaHasta=2026-10-06",
            "fechaDesde=ayer", "q=ana&q=bruno", "telefono=099999999")) {
            assertThat(getAs(company.admin(), listing(offer) + "?" + query).getResponse().getStatus()).isEqualTo(400);
        }
    }

    private Company company() {
        String email = UUID.randomUUID() + "@example.test";
        UUID id = provisioner.create("Empresa RF6 " + UUID.randomUUID(), new RegisterRequest(email, "Admin", "Prueba", PASSWORD), "tests");
        return new Company(id, accounts.byEmail(email).orElseThrow().id());
    }
    private UUID talent(String name, List<String> specializations) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        registration.register(Sujeto.visitante(), new RegisterRequest(email, name, "Prueba", PASSWORD));
        UUID id = accounts.byEmail(email).orElseThrow().id();
        jdbc.update("UPDATE perfil_talento SET telefono = '099999999', especializaciones = ? WHERE usuario_id = ?",
            json.writeValueAsString(specializations), id);
        return id;
    }
    private UUID offer(Company company, boolean published) throws Exception {
        var created = send(company.admin(), "POST", "/api/company/offers",
            "{\"titulo\":\"Oferta RF6\",\"descripcion\":\"Descripción válida\",\"cursosRequeridos\":[]}");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID id = UUID.fromString(tree(created).path("id").asText());
        if (published) assertThat(send(company.admin(), "POST", "/api/company/offers/" + id + "/publish", "{}")
            .getResponse().getStatus()).isEqualTo(200);
        return id;
    }
    private UUID upload(UUID talent, byte[] content) throws Exception {
        MvcResult uploaded = mvc.perform(multipart("/api/profile/me/curriculum")
            .file(new MockMultipartFile("archivo", "curriculum.pdf", "application/pdf", content))
            .with(user(talent.toString())).with(csrf().asHeader())).andReturn();
        assertThat(uploaded.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(tree(uploaded).path("id").asText());
    }
    private JsonNode apply(UUID talent, UUID offer) throws Exception {
        MvcResult applied = send(talent, "POST", "/api/offers/" + offer + "/applications", "{}");
        assertThat(applied.getResponse().getStatus()).isEqualTo(201);
        return tree(applied);
    }
    private int applyStatus(UUID talent, UUID offer) {
        try { return send(talent, "POST", "/api/offers/" + offer + "/applications", "{}").getResponse().getStatus(); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private MvcResult send(UUID userId, String method, String path, String body) throws Exception {
        return mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).with(user(userId.toString()))
            .with(csrf().asHeader()).contentType("application/json").content(body)).andReturn();
    }
    private MvcResult getAs(UUID userId, String path) throws Exception {
        return mvc.perform(get(URI.create(path)).with(user(userId.toString()))).andReturn();
    }
    private JsonNode tree(MvcResult response) throws Exception { return json.readTree(response.getResponse().getContentAsByteArray()); }
    private static String listing(UUID offer) { return "/api/company/offers/" + offer + "/applications"; }
    private static String nested(UUID offer, UUID application, String suffix) { return listing(offer) + "/" + application + "/" + suffix; }
    private static void assertSafeProfile(JsonNode profile) {
        assertThat(profile.size()).isEqualTo(4);
        assertThat(profile.has("nombre") && profile.has("apellido") && profile.has("correo") && profile.has("especializaciones")).isTrue();
        assertThat(profile.has("telefono")).isFalse();
    }
}
