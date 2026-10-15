package uy.edu.um.xperience;

import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import uy.edu.um.xperience.application.JobApplication;
import uy.edu.um.xperience.curriculum.Curriculum;
import uy.edu.um.xperience.offer.Offer;
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
@ExtendWith(OutputCaptureExtension.class)
class PolicyContractTest {
    @Autowired MockMvc mvc;
    @Autowired AuthorizationService policy;
    @MockitoSpyBean Sujetos subjects;
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
        @GetMapping("/test/outside-api") @RequiereAccion("catalogo.ver")
        public String outsideApi() { return "must never run"; }
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
    @Test void errorPageAndRoutesOutsideApiCannotBeRequestedDirectly() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(get("/test/outside-api")).andExpect(status().isUnauthorized());
        var subject = admin();
        mvc.perform(get("/error").with(user(subject.usuarioId().toString()))).andExpect(status().isForbidden());
        verify(registry).denegado(eq(subject), anyString(), eq("/error"));
    }
    @Test void denialsKeepTheirGenericResponseWhenTheAccessLogFails() throws Exception {
        var subject = admin();
        doThrow(new IllegalStateException("simulated access log outage")).when(registry).denegado(any(), any(), any());
        mvc.perform(get("/api/test/no-action"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales o sesión inválidas."));
        mvc.perform(get("/api/test/no-action").with(user(subject.usuarioId().toString())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Acceso denegado."));
    }
    @Test void policyDatabaseErrorsDenyBothFunctionAndRowsAndAreReportedByThePep(CapturedOutput output) throws Exception {
        var subject = admin();
        doThrow(new DataAccessResourceFailureException("simulated outage")).when(permissions).findByRolAndAccion(anyString(), anyString());
        assertThat(policy.puedeInvocar(subject, "cuenta.gestionar")).isFalse();
        assertThat(policy.autorizar(subject, "cuenta.gestionar", ResourceAccess.own(subject.usuarioId()))).isFalse();
        assertThat(users.findAll(policy.filtrar(subject, "cuenta.gestionar", Usuario.class))).isEmpty();
        mvc.perform(get("/api/account").with(user(subject.usuarioId().toString()))).andExpect(status().isForbidden());
        verify(registry).denegado(eq(subject), eq("cuenta.gestionar"), eq("/api/account"));
        assertThat(output).contains("Fallo la evaluacion de la politica (puedeInvocar de cuenta.gestionar); se deniega",
            "simulated outage");
    }
    @Test void errorsInsideThePepDenyGrantedActionsAndAreLoggedAndReported(CapturedOutput output) throws Exception {
        var subject = admin();
        doThrow(new IllegalStateException("simulated subject failure")).when(subjects).resolve(any());
        mvc.perform(get("/api/account").with(user(subject.usuarioId().toString())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Acceso denegado."));
        verify(registry).denegado(any(), eq("cuenta.gestionar"), eq("/api/account"));
        assertThat(output).contains("Fallo el PEP evaluando /api/account; se deniega", "simulated subject failure");
    }
    @Test void errorsWhileEvaluatingLoginDenyItBeforeCheckingCredentials(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException("simulated subject failure")).when(subjects).current();
        mvc.perform(post("/api/auth/login").with(csrf()).param("correo", "absent@example.test").param("password", "secret"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales o sesión inválidas."));
        verify(registry).denegado(any(), eq("sesion.iniciar"), eq("/api/auth/login"));
        assertThat(output).contains("Fallo la evaluacion de sesion.iniciar; se deniega", "simulated subject failure");
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
    @Test void applicantScopeRequiresTheExactPersistedApplicationAndItsCurriculumSnapshot() {
        var companyA = admin();
        var companyB = admin();
        UUID talentId = accounts.create(UUID.randomUUID() + "@example.test", "Ana", "Postulante", "unused-hash", "TALENTO");
        jdbc.update("INSERT INTO perfil_talento(usuario_id) VALUES (?)", talentId);
        UUID offerId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO oferta(id, empresa_id, titulo, descripcion, estado, creado_por)
            VALUES (?, ?, 'Oferta', 'Descripción', 'PUBLICADA', ?)
            """, offerId, companyA.empresaId(), companyA.usuarioId());
        UUID originalCv = UUID.randomUUID();
        UUID replacementCv = UUID.randomUUID();
        for (UUID cvId : List.of(originalCv, replacementCv)) {
            jdbc.update("""
                INSERT INTO curriculum(id, usuario_id, nombre_original, tipo_mime, tamanio_bytes, clave_almacen)
                VALUES (?, ?, 'cv.pdf', 'application/pdf', 20, ?)
                """, cvId, talentId, UUID.randomUUID().toString());
        }
        UUID applicationId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO postulacion(id, talento_id, oferta_id, empresa_id, curriculum_id)
            VALUES (?, ?, ?, ?, ?)
            """, applicationId, talentId, offerId, companyA.empresaId(), originalCv);
        var application = new JobApplication(applicationId, talentId, offerId, companyA.empresaId(),
            originalCv, "ENVIADA", OffsetDateTime.now());
        var context = new ApplicantAccess(application);

        assertThat(policy.autorizar(companyA, "perfil.ver_postulante", context)).isTrue();
        assertThat(policy.autorizar(companyA, "curriculum.descargar", context)).isTrue();
        assertThat(policy.camposLegibles(companyA, "perfil.ver_postulante", context))
            .containsExactlyInAnyOrder("nombre", "apellido", "correo", "especializaciones");
        assertThat(policy.camposLegibles(companyA, "curriculum.descargar", context))
            .containsExactlyInAnyOrder("id", "nombreOriginal", "tipoMime", "tamanioBytes", "creadoEn");
        assertThat(policy.autorizar(companyB, "perfil.ver_postulante", context)).isFalse();
        assertThat(policy.autorizar(companyB, "curriculum.descargar", context)).isFalse();
        assertThat(policy.autorizar(companyA, "perfil.ver_postulante", application)).isFalse();
        assertThat(policy.autorizar(companyA, "perfil.ver_postulante",
            new ResourceAccess(talentId, companyA.empresaId(), true))).isFalse();

        for (var forged : List.of(
                new JobApplication(UUID.randomUUID(), talentId, offerId, companyA.empresaId(), originalCv, "ENVIADA", application.fecha()),
                new JobApplication(applicationId, UUID.randomUUID(), offerId, companyA.empresaId(), originalCv, "ENVIADA", application.fecha()),
                new JobApplication(applicationId, talentId, UUID.randomUUID(), companyA.empresaId(), originalCv, "ENVIADA", application.fecha()),
                new JobApplication(applicationId, talentId, offerId, companyB.empresaId(), originalCv, "ENVIADA", application.fecha()),
                new JobApplication(applicationId, talentId, offerId, companyA.empresaId(), replacementCv, "ENVIADA", application.fecha()),
                new JobApplication(applicationId, talentId, offerId, companyA.empresaId(), originalCv, "SELECCIONADA", application.fecha()))) {
            assertThat(policy.autorizar(companyA, "perfil.ver_postulante", new ApplicantAccess(forged))).isFalse();
            assertThat(policy.autorizar(companyA, "curriculum.descargar", new ApplicantAccess(forged))).isFalse();
        }
        // Replacing the current CV must preserve access to the version fixed in this application.
        jdbc.update("UPDATE perfil_talento SET curriculum_actual_id = ? WHERE usuario_id = ?", replacementCv, talentId);
        assertThat(policy.autorizar(companyA, "curriculum.descargar", context)).isTrue();
        var replacement = new Curriculum(replacementCv, talentId, "cv.pdf", "application/pdf", 20,
            UUID.randomUUID().toString(), OffsetDateTime.now());
        assertThat(policy.autorizar(companyA, "curriculum.descargar", replacement)).isFalse();
        jdbc.update("DELETE FROM postulacion WHERE id = ?", applicationId);
        assertThat(policy.autorizar(companyA, "perfil.ver_postulante", context)).isFalse();
        assertThat(policy.autorizar(companyA, "curriculum.descargar", context)).isFalse();
    }
    @Test void rf6FieldContractsExcludeSystemFieldsAndPublishedOffersCannotBeEdited() {
        var subject = admin();
        var draft = new Offer(UUID.randomUUID(), subject.empresaId(), "Oferta", "Descripción", "BORRADOR",
            OffsetDateTime.now(), OffsetDateTime.now());
        var published = new Offer(draft.id(), draft.empresaId(), draft.titulo(), draft.descripcion(), "PUBLICADA",
            draft.creadoEn(), draft.actualizadoEn());
        assertThat(policy.camposEscribibles(subject, "oferta.gestionar", draft))
            .containsExactlyInAnyOrder("titulo", "descripcion", "cursosRequeridos");
        assertThat(policy.camposEscribibles(subject, "oferta.gestionar", published)).isEmpty();
        assertThat(policy.camposLegibles(subject, "oferta.gestionar", published))
            .doesNotContain("empresaId", "creadoPor", "password", "passwordHash");
        var talentId = accounts.create(UUID.randomUUID() + "@example.test", "Talento", "Prueba", "unused-hash", "TALENTO");
        var talent = Sujeto.from(accounts.byId(talentId).orElseThrow());
        assertThat(policy.autorizar(talent, "postulacion.crear", published)).isTrue();
        assertThat(policy.camposEscribibles(talent, "postulacion.crear", published)).isEmpty();
        assertThat(policy.autorizar(talent, "oferta.ver", draft)).isFalse();
        assertThat(policy.camposEscribibles(talent, "oferta.gestionar", draft)).isEmpty();
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
