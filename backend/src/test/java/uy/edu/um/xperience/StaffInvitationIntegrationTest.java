package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.*;
import uy.edu.um.xperience.staff.InvitationDelivery;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaffInvitationIntegrationTest {
    static final String PASSWORD = "una frase de prueba segura";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationService policy;
    @MockitoBean InvitationDelivery delivery;

    @Test void invitationAcceptanceAndRoleChangesApplyImmediately() throws Exception {
        var company = company(); var admin = company.browser();
        String email = email(); String token = invite(admin, email);
        var member = accounts.byEmail(email).orElseThrow();
        assertThat(member.rol()).isNull();
        assertThat(member.membresiaEstado()).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT invitacion_hash FROM membresia WHERE usuario_id = ?", String.class, member.id()))
            .isEqualTo(InvitationTokens.hash(token)).isNotEqualTo(token);
        var staff = new Browser();
        assertThat(staff.login(email).statusCode()).isEqualTo(401);
        assertThat(staff.accept(token, acceptance(), true).statusCode()).isEqualTo(204);
        assertThat(accounts.byEmail(email).orElseThrow().rol()).isNull();
        assertThat(staff.login(email).statusCode()).isEqualTo(401);
        assertThat(staff.get("/company/courses").statusCode()).isEqualTo(401);
        assertThat(admin.assign(member.id(), "RECLUTADOR").statusCode()).isEqualTo(403);
        assertThat(admin.reauth().statusCode()).isEqualTo(204);
        assertThat(admin.assign(member.id(), "RECLUTADOR").statusCode()).isEqualTo(204);
        assertThat(staff.login(email).statusCode()).isEqualTo(204);
        for (String action : List.of("oferta.gestionar", "postulacion.ver_listado")) {
            assertThat(policy.allowed(member.id(), action, ResourceAccess.company(company.id()))).isTrue();
            assertThat(policy.allowed(member.id(), action, ResourceAccess.company(UUID.randomUUID()))).isFalse();
        }
        assertThat(policy.autorizar(Sujeto.from(accounts.byId(member.id()).orElseThrow()), "postulacion.cambiar_estado",
            new TransicionPostulacion(company.id(), "enviada", "preseleccionada"))).isTrue();
        assertThat(staff.get("/company/courses").statusCode()).isEqualTo(403);
        assertThat(staff.assign(company.adminId(), "EDITOR").statusCode()).isEqualTo(403);
        assertThat(admin.assign(member.id(), "EDITOR").statusCode()).isEqualTo(204);
        // The same authenticated client gains content access and loses recruiting access.
        assertThat(staff.get("/company/courses").statusCode()).isEqualTo(200);
        assertThat(policy.allowed(member.id(), "oferta.gestionar", ResourceAccess.company(company.id()))).isFalse();
        assertThat(policy.allowed(member.id(), "postulacion.ver_listado", ResourceAccess.company(company.id()))).isFalse();
        assertThat(staff.get("/staff").statusCode()).isEqualTo(403);
        assertThat(staff.invite(email()).statusCode()).isEqualTo(403);
        assertThat(staff.assign(company.adminId(), "ADMIN").statusCode()).isEqualTo(403);
        assertThat(admin.assign(member.id(), "ADMIN").statusCode()).isEqualTo(204);
        assertThat(staff.get("/staff").statusCode()).isEqualTo(200);
        assertThat(staff.invite(email()).statusCode()).isEqualTo(202);
    }

    @Test void tokensCannotBeReplayedAndDoNotAppearInResponses() throws Exception {
        var company = company(); String email = email(); String token = invite(company.browser(), email);
        var visitor = new Browser();
        var listing = company.browser().get("/staff");
        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(listing.body()).doesNotContain(token, InvitationTokens.hash(token), "invitacionHash", "password");
        assertThat(visitor.accept(token, acceptance(), true).statusCode()).isEqualTo(204);
        assertThat(visitor.accept(token, acceptance(), true).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT invitacion_hash FROM membresia WHERE usuario_id = ?", String.class,
            accounts.byEmail(email).orElseThrow().id())).isNull();
    }

    @Test void expiredInvalidAndMissingTokensAreDeniedAndResendingRotatesTheToken() throws Exception {
        var company = company(); String email = email(); String token = invite(company.browser(), email);
        jdbc.update("UPDATE membresia SET invitacion_expira = ? WHERE usuario_id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
            accounts.byEmail(email).orElseThrow().id());
        var visitor = new Browser();
        for (String invalid : List.of(token, "bad-token", "a".repeat(43))) {
            assertThat(visitor.accept(invalid, acceptance(), true).statusCode()).isEqualTo(401);
        }
        assertThat(visitor.accept(null, acceptance(), true).statusCode()).isEqualTo(401);
        clearInvocations(delivery);
        String replacement = invite(company.browser(), email);
        assertThat(replacement).isNotEqualTo(token);
        assertThat(visitor.accept(token, acceptance(), true).statusCode()).isEqualTo(401);
        assertThat(visitor.accept(replacement, acceptance(), true).statusCode()).isEqualTo(204);
    }

    @ParameterizedTest @ValueSource(strings = {"rol", "empresaId", "usuarioId", "correo", "estado", "tipoCuenta"})
    void acceptanceRejectsSystemFieldsWithoutConsumingToken(String field) throws Exception {
        var company = company(); String token = invite(company.browser(), email());
        var payload = json.createObjectNode().put("nombre", "Invitado").put("apellido", "Prueba").put("password", PASSWORD);
        payload.put(field, "ADMIN");
        var visitor = new Browser();
        assertThat(visitor.accept(token, payload.toString(), true).statusCode()).isEqualTo(400);
        assertThat(visitor.accept(token, acceptance(), true).statusCode()).isEqualTo(204);
    }

    @Test void invitationsRequirePermissionAndCsrfAndAcceptanceRequiresCsrfAndValidData() throws Exception {
        var company = company(); var visitor = new Browser();
        assertThat(visitor.invite(email()).statusCode()).isEqualTo(401);
        String talentEmail = email();
        assertThat(visitor.write("POST", "/auth/register", json.writeValueAsString(new RegisterRequest(talentEmail, "Ana", "Test", PASSWORD)), true, null).statusCode()).isEqualTo(202);
        assertThat(visitor.login(talentEmail).statusCode()).isEqualTo(204);
        assertThat(visitor.invite(email()).statusCode()).isEqualTo(403);
        assertThat(company.browser().write("POST", "/staff/invitations", "{\"correo\":\"" + email() + "\"}", false, null).statusCode()).isEqualTo(403);
        assertThat(company.browser().write("POST", "/staff/invitations", "{\"correo\":\"" + email() + "\",\"rol\":\"ADMIN\"}", true, null).statusCode()).isEqualTo(400);
        String token = invite(company.browser(), email());
        var guest = new Browser();
        assertThat(guest.accept(token, acceptance(), false).statusCode()).isEqualTo(403);
        assertThat(guest.accept(token, "{\"nombre\":\" \",\"apellido\":\"Test\",\"password\":\"short\"}", true).statusCode()).isEqualTo(400);
        assertThat(guest.accept(token, acceptance(), true).statusCode()).isEqualTo(204);
    }

    @Test void companyIsolationAndExistingAccountsCannotBeTakenOver() throws Exception {
        var e1 = company(); var e2 = company(); String email = email();
        String token = invite(e1.browser(), email);
        var member = accounts.byEmail(email).orElseThrow();
        clearInvocations(delivery);
        var duplicate = e2.browser().invite(email);
        assertThat(duplicate.statusCode()).isEqualTo(202);
        verifyNoInteractions(delivery);
        assertThat(accounts.byId(member.id()).orElseThrow().empresaId()).isEqualTo(e1.id());
        assertThat(e2.browser().get("/staff").body()).doesNotContain(member.id().toString());
        assertThat(new Browser().accept(token, acceptance(), true).statusCode()).isEqualTo(204);
        e2.browser().reauth();
        assertThat(e2.browser().assign(member.id(), "ADMIN").statusCode()).isEqualTo(403);
        assertThat(accounts.byId(member.id()).orElseThrow().rol()).isNull();
        assertThat(e1.browser().invite(e2.email()).body()).isEqualTo(duplicate.body());
        verifyNoInteractions(delivery);
    }

    @Test void pendingMembershipCannotReceiveARoleAndInactiveInvitationCannotBeAccepted() throws Exception {
        var company = company(); String email = email(); String token = invite(company.browser(), email);
        UUID id = accounts.byEmail(email).orElseThrow().id();
        company.browser().reauth();
        assertThat(company.browser().assign(id, "ADMIN").statusCode()).isEqualTo(403);
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", id);
        assertThat(new Browser().accept(token, acceptance(), true).statusCode()).isEqualTo(401);
    }

    @Test void concurrentAcceptanceHasExactlyOneWinner() throws Exception {
        var company = company(); String token = invite(company.browser(), email());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> new Browser().accept(token, acceptance(), true).statusCode());
            var b = executor.submit(() -> new Browser().accept(token, acceptance(), true).statusCode());
            var results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(code -> code == 204).count()).isEqualTo(1);
            assertThat(results).allMatch(code -> code == 204 || code == 401 || code == 403);
        } finally { executor.shutdownNow(); }
    }

    @Test void failedDeliveryRollsBackInvitationAndAccount() throws Exception {
        var company = company(); String email = email();
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
            .when(delivery).send(eq(email), anyString());
        assertThat(company.browser().invite(email).statusCode()).isEqualTo(503);
        assertThat(accounts.byEmail(email)).isEmpty();
    }

    private record Company(UUID id, UUID adminId, String email, Browser browser) {}
    private Company company() throws Exception {
        String email = email();
        UUID id = provisioner.create("Empresa RF7", new RegisterRequest(email, "Admin", "Test", PASSWORD), "tests");
        var browser = new Browser(); assertThat(browser.login(email).statusCode()).isEqualTo(204);
        return new Company(id, accounts.byEmail(email).orElseThrow().id(), email, browser);
    }
    private String invite(Browser admin, String email) throws Exception {
        var response = admin.invite(email); assertThat(response.statusCode()).isEqualTo(202);
        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(delivery).send(eq(email), token.capture());
        assertThat(response.body()).doesNotContain(token.getValue());
        return token.getValue();
    }
    private static String email() { return UUID.randomUUID() + "@example.test"; }
    private String acceptance() throws Exception { return json.writeValueAsString(Map.of("nombre", "Invitado", "apellido", "Test", "password", PASSWORD)); }
    private class Browser {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> write(String method, String path, String body, boolean csrf, String invitation) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
            if (csrf) {
                var token = json.readTree(get("/auth/csrf").body());
                request.header(token.path("headerName").asText(), token.path("token").asText());
            }
            if (invitation != null) request.header(InvitationTokens.HEADER, invitation);
            return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> login(String email) throws Exception {
            var csrf = json.readTree(get("/auth/csrf").body());
            return client.send(HttpRequest.newBuilder(uri("/auth/login"))
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> invite(String email) throws Exception { return write("POST", "/staff/invitations", json.writeValueAsString(Map.of("correo", email)), true, null); }
        HttpResponse<String> accept(String token, String body, boolean csrf) throws Exception { return write("POST", "/invitations/accept", body, csrf, token); }
        HttpResponse<String> assign(UUID member, String role) throws Exception { return write("PATCH", "/staff/" + member + "/rol", json.writeValueAsString(Map.of("rol", role)), true, null); }
        HttpResponse<String> reauth() throws Exception { return write("POST", "/auth/reauthenticate", json.writeValueAsString(Map.of("password", PASSWORD)), true, null); }
    }
}
