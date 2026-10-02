package uy.edu.um.xperience;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.persistence.*;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PolicyContractTest.Probes.class)
class PolicyContractTest {
    @Autowired MockMvc mvc;
    @Autowired AuthorizationService policy;
    @Autowired Sujetos subjects;
    @Autowired Usuarios users;
    @Autowired Membresias memberships;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean Permisos permissions;
    @MockitoBean RegistroAccesos registry;

    @TestConfiguration
    @RestController
    public static class Probes {
        @GetMapping("/api/test/no-action") public String missing() { return "must never run"; }
        @GetMapping("/api/test/unknown-action") @RequiereAccion("no.declarada")
        public String unknown() { return "must never run"; }
    }
    private Sujeto admin() {
        String email = UUID.randomUUID() + "@example.test";
        provisioner.create("Policy test", new RegisterRequest(email, "Admin", "Test", "una frase suficientemente larga"), "tests");
        return Sujeto.from(accounts.byEmail(email).orElseThrow());
    }
    @Test void endpointsWithoutDeclaredOrGrantedActionAreDeniedAndReported() throws Exception {
        var subject = admin();
        for (String path : List.of("/api/test/no-action", "/api/test/unknown-action")) {
            mvc.perform(get(path).with(user(subject.usuarioId().toString()))).andExpect(status().isForbidden());
            verify(registry).denegado(eq(subject), anyString(), eq(path));
        }
    }
    @Test void policyDatabaseErrorsDenyBothFunctionAndRowsAndAreReportedByThePep() throws Exception {
        var subject = admin();
        doThrow(new DataAccessResourceFailureException("simulated outage")).when(permissions).findByRolAndAccion(anyString(), anyString());
        assertThat(policy.puedeInvocar(subject, "cuenta.gestionar")).isFalse();
        assertThat(policy.autorizar(subject, "cuenta.gestionar", ResourceAccess.own(subject.usuarioId()))).isFalse();
        assertThat(users.findAll(policy.filtrar(subject, "cuenta.gestionar", Usuario.class))).isEmpty();
        mvc.perform(get("/api/account").with(user(subject.usuarioId().toString()))).andExpect(status().isForbidden());
        verify(registry).denegado(eq(subject), eq("cuenta.gestionar"), eq("/api/account"));
    }
    @Test void loginAndLogoutUseThePermissionTableEvenThoughTheyAreSecurityFilters() throws Exception {
        doReturn(List.of()).when(permissions).findByRolAndAccion("VISITANTE", "sesion.iniciar");
        mvc.perform(post("/api/auth/login").with(csrf()).param("correo", "absent@example.test").param("password", "secret"))
            .andExpect(status().isUnauthorized());
        verify(registry).denegado(eq(Sujeto.visitante()), eq("sesion.iniciar"), eq("/api/auth/login"));
        var subject = admin();
        doReturn(List.of()).when(permissions).findByRolAndAccion("ADMIN", "sesion.cerrar");
        mvc.perform(post("/api/auth/logout").with(user(subject.usuarioId().toString())).with(csrf()))
            .andExpect(status().isForbidden());
        verify(registry).denegado(eq(subject), eq("sesion.cerrar"), eq("/api/auth/logout"));
    }
    @Test void forbiddenRegistrationFieldsAreRejectedAndReportedWithoutIncludingThePassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
            .content("{\"correo\":\"test@example.test\",\"nombre\":\"A\",\"apellido\":\"B\",\"password\":\"una frase suficientemente larga\",\"rol\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest());
        verify(registry).denegado(eq(Sujeto.visitante()), eq("cuenta.registrar"), eq("/api/auth/register"));
    }

    // RS12: un campo protegido rechaza toda la actualización
    // y el intento queda registrado en la auditoría.
    @Test
    void forbiddenProfileFieldsRejectWholeUpdateAndAreReported()
            throws Exception {
        String email = UUID.randomUUID() + "@example.test";

        mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "correo": "%s",
                      "nombre": "Ana",
                      "apellido": "Prueba",
                      "password": "una frase suficientemente larga"
                    }
                    """.formatted(email))
        ).andExpect(status().isAccepted());

        var account = accounts.byEmail(email).orElseThrow();
        var subject = Sujeto.from(account);

        clearInvocations(registry);

        mvc.perform(
            patch("/api/profile/me")
                .with(user(account.id().toString()))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "nombre": "NoDebeGuardarse",
                      "rol": "ADMIN"
                    }
                    """)
        ).andExpect(status().isBadRequest());

        assertThat(
            accounts.byEmail(email).orElseThrow().nombre()
        ).isEqualTo("Ana");

        verify(registry).denegado(
            eq(subject),
            eq("perfil.gestionar"),
            eq("/api/profile/me")
        );
    }

    @Test void ownAccountQueriesCannotReturnAnotherAccountAndFieldsNeverExposeSecrets() {
        var first = admin(); var second = admin();
        var scope = policy.filtrar(first, "cuenta.gestionar", Usuario.class);
        assertThat(users.findAll(scope)).extracting(u -> u.id).containsExactly(first.usuarioId());
        assertThat(users.findOne(scope.and((root, query, cb) -> cb.equal(root.get("id"), second.usuarioId())))).isEmpty();
        var own = users.findOne(scope).orElseThrow();
        assertThat(policy.camposLegibles(first, "cuenta.gestionar", own)).doesNotContain("password", "passwordHash", "password_hash");
        assertThat(policy.camposEscribibles(first, "cuenta.gestionar", own)).doesNotContain("id", "empresaId", "rol", "tipoCuenta");
        assertThat(policy.camposLegibles(second, "cuenta.gestionar", own)).isEmpty();
    }
    @Test void expiredOrDifferentSessionProofCannotAuthorizeRoleChanges() {
        var subject = admin();
        var target = new Membresia(); target.usuarioId = UUID.randomUUID(); target.empresaId = subject.empresaId(); target.estado = "ACTIVA";
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(policy.autorizar(subject, "staff.cambiar_rol", target)).isFalse();
            request.getSession().setAttribute(Reautenticacion.KEY,
                new Reautenticacion.Proof(subject.usuarioId(), subject.empresaId(), subject.rol(), Instant.now().minusSeconds(301)));
            assertThat(policy.autorizar(subject, "staff.cambiar_rol", target)).isFalse();
            assertThat(memberships.findAll(policy.filtrar(subject, "staff.cambiar_rol", Membresia.class))).isEmpty();
            request.getSession().setAttribute(Reautenticacion.KEY,
                new Reautenticacion.Proof(UUID.randomUUID(), subject.empresaId(), subject.rol(), Instant.now()));
            assertThat(policy.autorizar(subject, "staff.cambiar_rol", target)).isFalse();
            request.getSession().setAttribute(Reautenticacion.KEY,
                new Reautenticacion.Proof(subject.usuarioId(), subject.empresaId(), subject.rol(), Instant.now()));
            assertThat(policy.autorizar(subject, "staff.cambiar_rol", target)).isTrue();
            target.usuarioId = subject.usuarioId();
            assertThat(policy.autorizar(subject, "staff.cambiar_rol", target)).isFalse();
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
    @Test void publishedCatalogIsAccessibleToVisitorAndAllAssignedRolesButNotDrafts() {
        var visitor = Sujeto.visitante();
        assertThat(policy.autorizar(visitor, "catalogo.ver", ResourceAccess.publication(true))).isTrue();
        assertThat(policy.autorizar(visitor, "catalogo.ver", ResourceAccess.publication(false))).isFalse();
        var subject = admin();
        for (String role : List.of("ADMIN", "RECLUTADOR", "EDITOR")) {
            jdbc.update("UPDATE membresia SET rol = ? WHERE usuario_id = ?", role, subject.usuarioId());
            var current = subjects.byId(subject.usuarioId());
            assertThat(policy.autorizar(current, "catalogo.ver", ResourceAccess.publication(true))).isTrue();
            assertThat(policy.autorizar(current, "oferta.ver", ResourceAccess.publication(true))).isTrue();
        }
        assertThat(policy.autorizar(visitor, "oferta.ver", ResourceAccess.publication(true))).isFalse();
    }
    @Test void applicationTransitionsRequireValidSequenceAndOwnCompany() {
        var s = admin();
        assertThat(policy.autorizar(s, "postulacion.cambiar_estado", new TransicionPostulacion(s.empresaId(), "enviada", "preseleccionada"))).isTrue();
        assertThat(policy.autorizar(s, "postulacion.cambiar_estado", new TransicionPostulacion(s.empresaId(), "enviada", "seleccionada"))).isFalse();
        assertThat(policy.autorizar(s, "postulacion.cambiar_estado", new TransicionPostulacion(s.empresaId(), "descartada", "preseleccionada"))).isFalse();
        assertThat(policy.autorizar(s, "postulacion.cambiar_estado", new TransicionPostulacion(UUID.randomUUID(), "enviada", "preseleccionada"))).isFalse();
    }
    @Test void persistedPermissionsMatchTheDocumentedMatrixExactly() throws Exception {
        var expected = new HashSet<String>();
        var roles = List.of("VISITANTE", "TALENTO", "ADMIN", "RECLUTADOR", "EDITOR");
        var scopes = Set.of("GLOBAL", "PUBLICADO", "PROPIO", "ORG", "POSTULANTE", "INSCRIPTO");
        for (String line : Files.readAllLines(Path.of("../docs/seguridad/modelo-autorizacion.md"))) {
            String[] columns = line.split("\\|", -1);
            if (columns.length != 9 || !columns[1].strip().matches("\u0060[^\u0060]+\u0060")) continue;
            String action = columns[1].strip().replace("\u0060", "");
            for (int i = 0; i < roles.size(); i++) {
                String scope = columns[i + 3].strip();
                if (scopes.contains(scope)) expected.add(roles.get(i) + ":" + action + ":" + scope);
            }
        }
        assertThat(expected).hasSize(54);
        assertThat(permissions.findAll()).map(p -> p.rol + ":" + p.accion + ":" + p.alcance).containsExactlyInAnyOrderElementsOf(expected);
    }
}
