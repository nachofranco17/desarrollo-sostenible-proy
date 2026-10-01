package uy.edu.um.xperience;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.AuthorizationService;
import uy.edu.um.xperience.security.ResourceAccess;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationService authorization;

    @Test
    void talentRegistrationLoginProtectedRouteAndLogout() throws Exception {
        var client = new Browser();
        String email = email();
        assertThat(client.get("/account").statusCode()).isEqualTo(401);
        var registration = client.register(email);
        assertThat(registration.statusCode()).isEqualTo(202);
        assertThat(client.get("/account").statusCode()).isEqualTo(401);
        Account stored = accounts.byEmail(email).orElseThrow();
        assertThat(stored.tipoCuenta()).isEqualTo("TALENTO");
        assertThat(stored.empresaId()).isNull();
        assertThat(stored.passwordHash()).doesNotContain(PASSWORD);
        assertThat(passwords.matches(PASSWORD, stored.passwordHash())).isTrue();
        String oldCookie = client.sessionCookie();
        var login = client.login(email, PASSWORD);
        assertThat(login.statusCode()).isEqualTo(204);
        String loggedCookie = client.sessionCookie();
        assertThat(loggedCookie).isNotEqualTo(oldCookie);
        assertThat(login.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax");
        var profile = client.get("/account");
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(profile.body()).path("rol").asText()).isEqualTo("TALENTO");
        assertThat(profile.body()).doesNotContain("password", "hash");
        assertThat(profile.headers().firstValue("cache-control").orElse("")).contains("no-store");
        assertThat(replay(oldCookie).statusCode()).isEqualTo(401);
        assertThat(client.post("/auth/logout", "", "application/json", true).statusCode()).isEqualTo(204);
        assertThat(client.get("/account").statusCode()).isEqualTo(401);
        assertThat(replay(loggedCookie).statusCode()).isEqualTo(401);
    }

    @Test
    void duplicateEmailIsGenericCaseInsensitiveAndDoesNotChangeOriginalAccount() throws Exception {
        var client = new Browser();
        String email = email();
        var first = client.register(email);
        var repeated = client.register("  " + email.toUpperCase(Locale.ROOT) + "  ");
        assertThat(repeated.statusCode()).isEqualTo(first.statusCode());
        assertThat(repeated.body()).isEqualTo(first.body());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE correo = ?", Integer.class, email)).isEqualTo(1);
        var replacement = new RegisterRequest(email, "Otro", "Nombre", "otra clave completamente distinta");
        assertThat(client.post("/auth/register", json.writeValueAsString(replacement), "application/json", true).body()).isEqualTo(first.body());
        assertThat(client.login(email.toUpperCase(Locale.ROOT), PASSWORD).statusCode()).isEqualTo(204);
        assertThat(accounts.byEmail(email).orElseThrow().nombre()).isEqualTo("Ana");
    }

    @Test
    void simultaneousRegistrationHasSameResponseAndCreatesOneAccount() throws Exception {
        var first = new Browser(); var second = new Browser();
        String email = email();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> a = executor.submit(() -> first.register(email));
            Future<HttpResponse<String>> b = executor.submit(() -> second.register(email));
            var responseA = a.get(20, TimeUnit.SECONDS); var responseB = b.get(20, TimeUnit.SECONDS);
            assertThat(responseA.statusCode()).isEqualTo(202);
            assertThat(responseB.statusCode()).isEqualTo(202);
            assertThat(responseA.body()).isEqualTo(responseB.body());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE correo = ?", Integer.class, email)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rol", "tipoCuenta", "empresaId", "activo", "empresa"})
    void registrationRejectsPrivilegeAndCompanyFields(String field) throws Exception {
        var client = new Browser();
        String email = email();
        var payload = json.valueToTree(new RegisterRequest(email, "Ana", "Prueba", PASSWORD));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put(field, "ADMIN");
        assertThat(client.post("/auth/register", json.writeValueAsString(payload), "application/json", true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email)).isEmpty();
    }

    @Test
    void noPublicCompanyRegistrationAndUnknownRoutesAreDenied() throws Exception {
        var client = new Browser();
        assertThat(client.post("/empresas", "{}", "application/json", true).statusCode()).isEqualTo(401);
        String email = email(); client.register(email); client.login(email, PASSWORD);
        assertThat(client.post("/empresas", "{}", "application/json", true).statusCode()).isEqualTo(403);
        assertThat(client.get("/unknown").statusCode()).isEqualTo(403);
    }

    @Test
    void invalidCredentialsAndNonexistentAccountHaveSameError() throws Exception {
        var client = new Browser();
        String email = email(); client.register(email);
        var wrong = client.login(email, "incorrecta");
        var absent = client.login(email(), PASSWORD);
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(absent.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).isEqualTo(absent.body());
        assertThat(client.get("/account").statusCode()).isEqualTo(401);
    }

    @Test
    void writesRequireCsrfIncludingLoginAndLogout() throws Exception {
        var client = new Browser();
        String email = email();
        var input = json.writeValueAsString(new RegisterRequest(email, "Ana", "Prueba", PASSWORD));
        assertThat(client.post("/auth/register", input, "application/json", false).statusCode()).isEqualTo(403);
        assertThat(accounts.byEmail(email)).isEmpty();
        client.register(email);
        assertThat(client.post("/auth/login", "correo=" + email + "&password=x", "application/x-www-form-urlencoded", false).statusCode()).isEqualTo(403);
        client.login(email, PASSWORD);
        assertThat(client.post("/auth/logout", "", "application/json", false).statusCode()).isEqualTo(403);
        assertThat(client.get("/account").statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsInvalidRegistrationWithoutCreatingAccount() throws Exception {
        var client = new Browser();
        String email = email();
        var invalid = new RegisterRequest(email, " ", "Prueba", "corta");
        assertThat(client.post("/auth/register", json.writeValueAsString(invalid), "application/json", true).statusCode()).isEqualTo(400);
        assertThat(accounts.byEmail(email)).isEmpty();
    }

    @Test
    void provisionCreatesCompanyAdminAndAuditAtomicallyAndAdminCanLogInAndOut() throws Exception {
        String email = email();
        var input = new RegisterRequest(email, "Admin", "Prueba", PASSWORD);
        UUID company = provisioner.create("Empresa de prueba", input, "test-suite");
        var admin = accounts.byEmail(email).orElseThrow();
        assertThat(admin.tipoCuenta()).isEqualTo("STAFF");
        assertThat(admin.rol()).isEqualTo("ADMIN");
        assertThat(admin.empresaId()).isEqualTo(company);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alta_empresa WHERE empresa_id = ? AND administrador_id = ?", Integer.class, company, admin.id())).isEqualTo(1);
        int companyCount = jdbc.queryForObject("SELECT COUNT(*) FROM empresa", Integer.class);
        assertThatThrownBy(() -> provisioner.create("No debe crearse", input, "test-suite")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM empresa", Integer.class)).isEqualTo(companyCount);
        var client = new Browser();
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        var profile = client.get("/account");
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(profile.body()).path("empresaId").asText()).isEqualTo(company.toString());
        assertThat(json.readTree(profile.body()).path("rol").asText()).isEqualTo("ADMIN");
        assertThat(client.post("/auth/logout", "", "application/json", true).statusCode()).isEqualTo(204);
        assertThat(client.get("/account").statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECLUTADOR", "EDITOR"})
    void allActiveStaffRolesCanSignInAndMembershipRevocationTakesEffect(String role) throws Exception {
        // RF7 is separate: this fixture represents an invitation already accepted and assigned a role.
        String email = email();
        provisioner.create("Empresa staff", new RegisterRequest(email, "Staff", "Prueba", PASSWORD), "test-suite");
        UUID id = accounts.byEmail(email).orElseThrow().id();
        jdbc.update("UPDATE membresia SET rol = ? WHERE usuario_id = ?", role, id);
        var client = new Browser();
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(json.readTree(client.get("/account").body()).path("rol").asText()).isEqualTo(role);
        jdbc.update("UPDATE membresia SET estado = 'BAJA' WHERE usuario_id = ?", id);
        assertThat(client.get("/account").statusCode()).isEqualTo(403);
        assertThat(client.get("/account").statusCode()).isEqualTo(401);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void inactiveAccountsCannotUseExistingSessionOrSignInAgain() throws Exception {
        String email = email(); var client = new Browser(); client.register(email); client.login(email, PASSWORD);
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE correo = ?", email);
        assertThat(client.get("/account").statusCode()).isEqualTo(403);
        assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void staffWithoutAssignedRoleCannotSignInOrInvokeAnyAction() throws Exception {
        var company = company();
        var member = acceptedStaff(company.companyId());
        assertThat(member.rol()).isNull();
        var browser = new Browser();
        assertThat(browser.login(member.correo(), PASSWORD).statusCode()).isEqualTo(401);
        var account = browser.get("/account");
        assertThat(account.statusCode()).isEqualTo(401);
        var resource = new ResourceAccess(member.id(), company.companyId(), true);
        for (String action : jdbc.queryForList("SELECT DISTINCT accion FROM permiso", String.class)) {
            assertThat(authorization.allowed(member.id(), action, resource)).as(action).isFalse();
        }
        assertThat(browser.patch("/staff/" + company.admin().id() + "/rol", "{\"rol\":\"RECLUTADOR\"}", true).statusCode()).isEqualTo(401);
        assertThat(accounts.byId(member.id()).orElseThrow().rol()).isNull();
    }

    @Test
    void newTalentHasExactlyTheMinimumBusinessPermissionsWithResourceRestrictions() throws Exception {
        String email = email(); var browser = new Browser();
        assertThat(browser.register(email).statusCode()).isEqualTo(202);
        var talent = accounts.byEmail(email).orElseThrow();
        assertThat(browser.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForList("SELECT accion FROM permiso WHERE rol = 'TALENTO'", String.class))
            .containsExactlyInAnyOrder("sesion.cerrar", "cuenta.gestionar", "perfil.gestionar", "catalogo.ver",
                "material.descargar", "inscripcion.crear", "inscripcion.ver", "leccion.completar", "entregable.responder",
                "oferta.ver", "postulacion.crear", "postulacion.ver", "curriculum.subir", "curriculum.descargar");
        for (String action : List.of("catalogo.ver", "inscripcion.crear", "postulacion.crear")) {
            assertThat(authorization.allowed(talent.id(), action, ResourceAccess.publication(true))).as(action).isTrue();
            assertThat(authorization.allowed(talent.id(), action, ResourceAccess.publication(false))).as(action + " borrador").isFalse();
        }
        assertThat(authorization.allowed(talent.id(), "perfil.gestionar", ResourceAccess.own(talent.id()))).isTrue();
        assertThat(authorization.allowed(talent.id(), "perfil.gestionar", ResourceAccess.own(UUID.randomUUID()))).isFalse();
        for (String action : List.of("curso.gestionar", "oferta.gestionar", "postulacion.ver_listado",
                "postulacion.cambiar_estado", "staff.cambiar_rol", "accion.no.declarada")) {
            assertThat(authorization.allowed(talent.id(), action, new ResourceAccess(talent.id(), UUID.randomUUID(), true)))
                .as(action).isFalse();
        }
    }

    @Test
    void explicitRecruiterAssignmentEnablesOnlyRecruitingInItsCompanyOnTheExistingSession() throws Exception {
        var e1 = company(); var e2 = company();
        var member = acceptedStaff(e1.companyId());
        var otherMember = acceptedStaff(e1.companyId());
        var staff = new Browser();
        assertThat(staff.login(member.correo(), PASSWORD).statusCode()).isEqualTo(401);
        assertThat(authorization.allowed(member.id(), "oferta.gestionar", ResourceAccess.company(e1.companyId()))).isFalse();
        var admin = new Browser();
        assertThat(admin.login(e1.admin().correo(), PASSWORD).statusCode()).isEqualTo(204);
        assertThat(admin.patch("/staff/" + member.id() + "/rol", "{\"rol\":\"RECLUTADOR\"}", true).statusCode()).isEqualTo(403);
        assertThat(admin.reauthenticate(PASSWORD).statusCode()).isEqualTo(204);
        assertThat(admin.patch("/staff/" + member.id() + "/rol", "{\"rol\":\"RECLUTADOR\"}", true).statusCode()).isEqualTo(204);
        assertThat(staff.login(member.correo(), PASSWORD).statusCode()).isEqualTo(204);
        String sessionBefore = staff.sessionCookie();
        var account = staff.get("/account");
        assertThat(account.statusCode()).isEqualTo(200);
        assertThat(json.readTree(account.body()).path("rol").asText()).isEqualTo("RECLUTADOR");
        assertThat(staff.sessionCookie()).isEqualTo(sessionBefore);
        for (String action : List.of("oferta.gestionar", "postulacion.ver_listado")) {
            assertThat(authorization.allowed(member.id(), action, ResourceAccess.company(e1.companyId()))).as(action + " E1").isTrue();
            assertThat(authorization.allowed(member.id(), action, ResourceAccess.company(e2.companyId()))).as(action + " E2").isFalse();
        }
        for (String action : List.of("curso.gestionar", "staff.cambiar_rol", "perfil.gestionar", "inscripcion.crear", "postulacion.crear", "accion.no.declarada")) {
            assertThat(authorization.allowed(member.id(), action, new ResourceAccess(member.id(), e1.companyId(), true)))
                .as(action).isFalse();
        }
        assertThat(staff.patch("/staff/" + otherMember.id() + "/rol", "{\"rol\":\"ADMIN\"}", true).statusCode()).isEqualTo(403);
        assertThat(accounts.byId(otherMember.id()).orElseThrow().rol()).isNull();

        // Reassignment replaces the role, without accumulating its previous permissions.
        assertThat(admin.patch("/staff/" + member.id() + "/rol", "{\"rol\":\"EDITOR\"}", true).statusCode()).isEqualTo(204);
        assertThat(authorization.allowed(member.id(), "oferta.gestionar", ResourceAccess.company(e1.companyId()))).isFalse();
        assertThat(authorization.allowed(member.id(), "curso.gestionar", ResourceAccess.company(e1.companyId()))).isTrue();
        assertThat(json.readTree(staff.get("/account").body()).path("rol").asText()).isEqualTo("EDITOR");
        assertThat(staff.sessionCookie()).isEqualTo(sessionBefore);
        jdbc.update("UPDATE membresia SET rol = NULL WHERE usuario_id = ?", member.id());
        assertThat(authorization.allowed(member.id(), "curso.gestionar", ResourceAccess.company(e1.companyId()))).isFalse();
        assertThat(staff.get("/account").statusCode()).isEqualTo(403);
        assertThat(staff.get("/account").statusCode()).isEqualTo(401);
    }

    @Test
    void roleAssignmentRejectsUnauthorizedInvalidAndCrossCompanyRequestsWithoutChangingMembership() throws Exception {
        var e1 = company(); var e2 = company(); var member = acceptedStaff(e1.companyId());
        String route = "/staff/" + member.id() + "/rol";
        String payload = "{\"rol\":\"RECLUTADOR\"}";
        var visitor = new Browser();
        assertThat(visitor.patch(route, payload, true).statusCode()).isEqualTo(401);
        var talent = new Browser(); String email = email(); talent.register(email); talent.login(email, PASSWORD);
        assertThat(talent.patch(route, payload, true).statusCode()).isEqualTo(403);
        var admin = new Browser(); admin.login(e2.admin().correo(), PASSWORD);
        assertThat(admin.reauthenticate(PASSWORD).statusCode()).isEqualTo(204);
        assertThat(admin.patch(route, payload, true).statusCode()).isEqualTo(403);
        admin.post("/auth/logout", "", "application/json", true);
        admin.login(e1.admin().correo(), PASSWORD);
        assertThat(admin.reauthenticate(PASSWORD).statusCode()).isEqualTo(204);
        assertThat(admin.patch(route, payload, false).statusCode()).isEqualTo(403);
        for (String invalid : List.of("{}", "{\"rol\":null}", "{\"rol\":\"TALENTO\"}", "{\"rol\":\"SUPERADMIN\"}",
                "{\"rol\":\"RECLUTADOR\",\"empresaId\":\"" + e2.companyId() + "\"}")) {
            assertThat(admin.patch(route, invalid, true).statusCode()).isEqualTo(400);
        }
        assertThat(admin.patch("/staff/" + e1.admin().id() + "/rol", payload, true).statusCode()).isEqualTo(403);
        assertThat(admin.patch("/staff/" + UUID.randomUUID() + "/rol", payload, true).statusCode()).isEqualTo(403);
        assertThat(admin.patch("/staff/" + accounts.byEmail(email).orElseThrow().id() + "/rol", payload, true).statusCode()).isEqualTo(403);
        assertThat(accounts.byId(member.id()).orElseThrow().rol()).isNull();
        assertThat(accounts.byId(member.id()).orElseThrow().empresaId()).isEqualTo(e1.companyId());
        assertThat(accounts.byId(e1.admin().id()).orElseThrow().rol()).isEqualTo("ADMIN");
    }

    @Test
    void pendingOrRevokedMembershipAndInactiveAccountsNeverGetBusinessPermissions() throws Exception {
        var company = company(); var member = acceptedStaff(company.companyId());
        jdbc.update("UPDATE membresia SET rol = 'RECLUTADOR' WHERE usuario_id = ?", member.id());
        for (String state : List.of("PENDIENTE", "BAJA")) {
            jdbc.update("UPDATE membresia SET estado = ? WHERE usuario_id = ?", state, member.id());
            assertThat(authorization.allowed(member.id(), "oferta.gestionar", ResourceAccess.company(company.companyId()))).isFalse();
            assertThat(new Browser().login(member.correo(), PASSWORD).statusCode()).isEqualTo(401);
        }
        jdbc.update("UPDATE membresia SET estado = 'ACTIVA' WHERE usuario_id = ?", member.id());
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", member.id());
        assertThat(authorization.allowed(member.id(), "oferta.gestionar", ResourceAccess.company(company.companyId()))).isFalse();
        assertThat(authorization.allowed(UUID.randomUUID(), "catalogo.ver", ResourceAccess.publication(true))).isFalse();
        assertThat(authorization.allowed(null, "catalogo.ver", ResourceAccess.publication(true))).isTrue();
        assertThat(authorization.allowed(company.admin().id(), "oferta.gestionar", null)).isFalse();
    }

    @Test
    void reauthenticationRequiresTheCurrentPasswordAndIsReservedForAdmins() throws Exception {
        var company = company(); var member = acceptedStaff(company.companyId());
        var admin = new Browser(); admin.login(company.admin().correo(), PASSWORD);
        String route = "/staff/" + member.id() + "/rol";
        String payload = "{\"rol\":\"RECLUTADOR\"}";
        assertThat(admin.reauthenticate("incorrecta").statusCode()).isEqualTo(401);
        assertThat(admin.patch(route, payload, true).statusCode()).isEqualTo(403);
        assertThat(admin.reauthenticate(PASSWORD).statusCode()).isEqualTo(204);
        assertThat(admin.reauthenticate("incorrecta").statusCode()).isEqualTo(401);
        assertThat(admin.patch(route, payload, true).statusCode()).isEqualTo(403);
        assertThat(accounts.byId(member.id()).orElseThrow().rol()).isNull();
        var talent = new Browser(); String email = email(); talent.register(email); talent.login(email, PASSWORD);
        assertThat(talent.reauthenticate(PASSWORD).statusCode()).isEqualTo(403);
    }

    private record CompanyFixture(UUID companyId, Account admin) {}
    private CompanyFixture company() {
        String email = email();
        UUID id = provisioner.create("Empresa permisos", new RegisterRequest(email, "Admin", "Prueba", PASSWORD), "test-suite");
        return new CompanyFixture(id, accounts.byEmail(email).orElseThrow());
    }

    private Account acceptedStaff(UUID companyId) {
        // Fixture for the post-acceptance state. Invitation delivery/acceptance belongs to RF7.
        String email = email();
        UUID id = accounts.create(email, "Invitado", "Prueba", passwords.encode(PASSWORD), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, estado) VALUES (?, ?, 'ACTIVA')", id, companyId);
        return accounts.byId(id).orElseThrow();
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> replay(String cookie) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/account"))
            .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, String body, String contentType, boolean csrf) throws Exception {
            return write("POST", path, body, contentType, csrf);
        }
        HttpResponse<String> patch(String path, String body, boolean csrf) throws Exception {
            return write("PATCH", path, body, "application/json", csrf);
        }
        HttpResponse<String> write(String method, String path, String body, String contentType, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType);
            if (csrf) {
                var token = json.readTree(get("/auth/csrf").body());
                request.header(token.path("headerName").asText(), token.path("token").asText());
            }
            return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> register(String email) throws Exception {
            return post("/auth/register", json.writeValueAsString(new RegisterRequest(email, "Ana", "Prueba", PASSWORD)), "application/json", true);
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            return post("/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), "application/x-www-form-urlencoded", true);
        }
        HttpResponse<String> reauthenticate(String password) throws Exception {
            return post("/auth/reauthenticate", json.writeValueAsString(Map.of("password", password)), "application/json", true);
        }
        String sessionCookie() {
            var cookie = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("SESSION")).findFirst().orElseThrow();
            return cookie.getName() + "=" + cookie.getValue();
        }
    }
}
