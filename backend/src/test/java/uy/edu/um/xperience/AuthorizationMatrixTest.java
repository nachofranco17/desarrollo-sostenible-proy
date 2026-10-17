package uy.edu.um.xperience;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.application.JobApplication;
import uy.edu.um.xperience.course.Course;
import uy.edu.um.xperience.course.CourseRepository;
import uy.edu.um.xperience.offer.Offer;
import uy.edu.um.xperience.security.*;
import static org.assertj.core.api.Assertions.assertThat;

/** RS20: oráculo documental independiente del evaluador y de la tabla permiso. */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMatrixTest {
    private static final List<String> ROLES = List.of("VISITANTE", "TALENTO", "ADMIN", "RECLUTADOR", "EDITOR");
    private static final Set<String> SESSION_ACTIONS = Set.of("sesion.iniciar", "sesion.cerrar", "sesion.reautenticar");
    // En el mismo orden que las 24 filas de SEG-R1. Una fila puede contener dos operaciones.
    private static final List<List<String>> R1_ACTIONS = List.of(
        List.of("cuenta.registrar"), List.of("cuenta.gestionar"), List.of("perfil.gestionar"),
        List.of("perfil.ver_postulante"), List.of("catalogo.ver"), List.of("curso.gestionar"),
        List.of("curso.ver_borrador"), List.of("material.subir"), List.of("material.descargar"),
        List.of("inscripcion.crear"), List.of("inscripcion.ver"), List.of("leccion.completar"),
        List.of("entregable.responder"), List.of("inscripcion.ver_inscriptos"), List.of("oferta.ver"),
        List.of("oferta.gestionar"), List.of("postulacion.crear"), List.of("postulacion.ver"),
        List.of("postulacion.ver_listado", "postulacion.cambiar_estado"), List.of("curriculum.subir"),
        List.of("curriculum.descargar"), List.of("staff.invitar"),
        List.of("staff.cambiar_rol", "staff.dar_baja"), List.of("staff.ver"));
    record Decision(String role, String action, String scope) {}
    @Autowired AuthorizationService policy;
    @Autowired AccountRepository accounts;
    @Autowired CourseRepository courses;
    @Autowired JdbcTemplate jdbc;
    private final Map<String, Sujeto> subjects = new HashMap<>();
    private UUID company;
    private UUID otherCompany;
    private UUID otherTalent;
    private Course enrolled;
    private Course notEnrolled;
    private Course enrolledProject;
    private Course notEnrolledProject;
    private ApplicantAccess applicant;
    private Offer publishedOffer;

    private static List<Decision> matrix() {
        try {
            var result = new ArrayList<Decision>();
            for (String line : Files.readAllLines(Path.of("../docs/seguridad/modelo-autorizacion.md"))) {
                var columns = line.split("\\|", -1);
                if (columns.length != 9 || !columns[1].strip().matches("`[^`]+`")) continue;
                for (int i = 0; i < ROLES.size(); i++) {
                    result.add(new Decision(ROLES.get(i), columns[1].strip().replace("`", ""), columns[i + 3].strip()));
                }
            }
            return result;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    static Stream<Decision> grants() { return matrix().stream().filter(d -> !"—".equals(d.scope())); }
    static Stream<Decision> forbidden() { return matrix().stream().filter(d -> "—".equals(d.scope())); }

    @BeforeAll void persistRealSubjectsAndRelations() {
        company = UUID.randomUUID(); otherCompany = UUID.randomUUID();
        for (UUID id : List.of(company, otherCompany)) jdbc.update("INSERT INTO empresa(id, nombre) VALUES (?, 'RS20')", id);
        subjects.put("VISITANTE", Sujeto.visitante());
        for (String role : ROLES.subList(1, ROLES.size())) subjects.put(role, createSubject(role));
        otherTalent = createSubject("TALENTO").usuarioId();
        enrolled = createCourse("PUBLICADO"); notEnrolled = createCourse("PUBLICADO");
        enrolledProject = createCourse("PROYECTO", "PUBLICADO");
        notEnrolledProject = createCourse("PROYECTO", "PUBLICADO");
        for (Course course : List.of(enrolled, enrolledProject)) {
            jdbc.update("INSERT INTO inscripcion(id, talento_id, curso_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), subjects.get("TALENTO").usuarioId(), course.id());
        }
        UUID offerId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO oferta(id, empresa_id, titulo, descripcion, estado, creado_por)
            VALUES (?, ?, 'RS20', 'Oferta publicada', 'PUBLICADA', ?)
            """, offerId, company, subjects.get("ADMIN").usuarioId());
        publishedOffer = new Offer(offerId, company, "RS20", "Oferta publicada", "PUBLICADA", OffsetDateTime.now(), OffsetDateTime.now());
        UUID cv = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO curriculum(id, usuario_id, nombre_original, tipo_mime, tamanio_bytes, clave_almacen)
            VALUES (?, ?, 'cv.pdf', 'application/pdf', 20, ?)
            """, cv, otherTalent, UUID.randomUUID().toString());
        UUID application = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO postulacion(id, talento_id, oferta_id, empresa_id, curriculum_id)
            VALUES (?, ?, ?, ?, ?)
            """, application, otherTalent, offerId, company, cv);
        applicant = new ApplicantAccess(new JobApplication(application, otherTalent, offerId, company, cv, "ENVIADA", OffsetDateTime.now()));
    }
    private Sujeto createSubject(String role) {
        boolean talent = "TALENTO".equals(role);
        UUID id = accounts.create(UUID.randomUUID() + "@example.test", "Matriz", role, "unused-test-hash", talent ? "TALENTO" : "STAFF");
        if (talent) jdbc.update("INSERT INTO perfil_talento(usuario_id) VALUES (?)", id);
        else jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')", id, company, role);
        return Sujeto.from(accounts.byId(id).orElseThrow());
    }
    private Course createCourse(String state) {
        return createCourse("CURSO", state);
    }
    private Course createCourse(String type, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO curso(id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel, duracion_horas, costo, estado, creado_por)
            VALUES (?, ?, ?, 'RS20', 'Contenido', 'Java', 'INICIAL', 10, 0, ?, ?)
            """, id, company, type, state, subjects.get("ADMIN").usuarioId());
        return courses.find(id, company).orElseThrow();
    }
    private void freshProof(Sujeto subject) {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute(Reautenticacion.KEY,
            new Reautenticacion.Proof(subject.usuarioId(), subject.empresaId(), subject.rol(), Instant.now()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test void actionTranslationCoversEveryR1RowAndEveryConsumerWithoutChangingItsMeaning() throws IOException {
        var lines = Files.readString(Path.of("../docs/seguridad/matriz-control-acceso.md"))
            .split("## Matriz\\R", 2)[1].split("### Alcances", 2)[0].lines()
            .map(line -> line.split("\\|", -1)).filter(c -> c.length == 9)
            .filter(c -> !Set.of("Recurso", "---").contains(c[1].strip())).toList();
        assertThat(lines).hasSize(R1_ACTIONS.size());
        var expected = new HashSet<Decision>();
        for (int row = 0; row < lines.size(); row++) {
            for (String action : R1_ACTIONS.get(row)) for (int role = 0; role < ROLES.size(); role++) {
                String cell = lines.get(row)[role + 3].strip();
                String scope = switch (cell) {
                    case "propio", "propia", "propias" -> "PROPIO";
                    case "org", "org (+R23)" -> "ORG";
                    case "postul." -> "POSTULANTE";
                    case "inscripto" -> "INSCRIPTO";
                    case "✓" -> "cuenta.registrar".equals(action) ? "GLOBAL" : "PUBLICADO";
                    case "—" -> "—";
                    default -> throw new AssertionError("Símbolo de SEG-R1 sin cobertura: " + cell);
                };
                expected.add(new Decision(ROLES.get(role), action, scope));
            }
        }
        assertThat(matrix()).hasSize(145).doesNotHaveDuplicates();
        assertThat(matrix().stream().filter(d -> !SESSION_ACTIONS.contains(d.action())).toList())
            .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(grants().count()).isEqualTo(54);
        assertThat(forbidden().count()).isEqualTo(91);
    }

    @ParameterizedTest(name = "permitido {0}") @MethodSource("grants")
    void everyGrantedCombinationHasAPositiveCase(Decision d) {
        var subject = subjects.get(d.role()); freshProof(subject);
        assertThat(policy.puedeInvocar(subject, d.action())).as("función %s", d).isTrue();
        assertThat(policy.autorizar(subject, d.action(), context(subject, d.action(), d.scope(), true)))
            .as("instancia permitida %s", d).isTrue();
    }

    @ParameterizedTest(name = "denegado fuera de alcance {0}") @MethodSource("grants")
    void everyGrantedCombinationHasANegativeCase(Decision d) {
        var subject = subjects.get(d.role()); freshProof(subject);
        assertThat(policy.autorizar(subject, d.action(), context(subject, d.action(), d.scope(), false)))
            .as("instancia fuera del alcance %s", d).isFalse();
        assertThat(policy.autorizar(subject, d.action(), null)).as("recurso ausente %s", d).isFalse();
    }

    @ParameterizedTest(name = "rol sin permiso {0}") @MethodSource("forbidden")
    void everyForbiddenRoleActionIsDeniedEvenWithFavorableResourceAttributes(Decision d) {
        var subject = subjects.get(d.role()); freshProof(subject);
        assertThat(policy.puedeInvocar(subject, d.action())).as("función denegada %s", d).isFalse();
        for (String scope : List.of("GLOBAL", "PROPIO", "ORG", "PUBLICADO", "INSCRIPTO", "POSTULANTE")) {
            assertThat(policy.autorizar(subject, d.action(), context(subject, d.action(), scope, true)))
                .as("rol denegado %s ante %s", d, scope).isFalse();
        }
    }

    private Object context(Sujeto subject, String action, String scope, boolean valid) {
        return switch (scope) {
            case "GLOBAL" -> valid ? AuthorizationService.Global.INSTANCE : ResourceAccess.own(otherTalent);
            case "PROPIO" -> ResourceAccess.own(valid ? subject.usuarioId() : otherTalent);
            case "ORG" -> "postulacion.cambiar_estado".equals(action)
                ? new TransicionPostulacion(valid ? company : otherCompany, "enviada", "preseleccionada")
                : new ResourceAccess(otherTalent, valid ? company : otherCompany, false);
            case "PUBLICADO" -> action.startsWith("oferta.") || "postulacion.crear".equals(action)
                ? (valid ? publishedOffer : new Offer(publishedOffer.id(), company, "RS20", "Borrador", "BORRADOR", OffsetDateTime.now(), OffsetDateTime.now()))
                : ResourceAccess.publication(valid);
            case "INSCRIPTO" -> "entregable.responder".equals(action)
                ? (valid ? enrolledProject : notEnrolledProject) : (valid ? enrolled : notEnrolled);
            case "POSTULANTE" -> valid ? applicant : new ApplicantAccess(new JobApplication(
                UUID.randomUUID(), otherTalent, publishedOffer.id(), company, applicant.application().curriculumId(), "ENVIADA", OffsetDateTime.now()));
            default -> throw new AssertionError("Alcance sin casos: " + scope);
        };
    }

    @Test void enrollmentIsPersistedAndSurvivesWithdrawalButCannotBeForged() {
        var talent = subjects.get("TALENTO");
        assertThat(policy.autorizar(talent, "material.descargar", ResourceAccess.own(talent.usuarioId()))).isFalse();
        assertThat(policy.autorizar(talent, "material.descargar", ResourceAccess.publication(true))).isFalse();
        try {
            for (Course course : List.of(enrolled, enrolledProject)) {
                jdbc.update("UPDATE curso SET estado = 'BAJADO' WHERE id = ?", course.id());
                var withdrawn = courses.find(course.id(), company).orElseThrow();
                var actions = course.isCurso() ? List.of("material.descargar", "leccion.completar")
                    : List.of("material.descargar", "entregable.responder");
                for (String action : actions) {
                    assertThat(policy.autorizar(talent, action, withdrawn)).as(action).isTrue();
                    assertThat(policy.autorizar(subjects.get("ADMIN"), action, notEnrolled))
                        .as("solo material concede ORG a Admin").isEqualTo("material.descargar".equals(action));
                }
                jdbc.update("DELETE FROM inscripcion WHERE talento_id = ? AND curso_id = ?", talent.usuarioId(), course.id());
                assertThat(policy.autorizar(talent, "material.descargar", withdrawn)).isFalse();
            }
        } finally {
            for (Course course : List.of(enrolled, enrolledProject)) {
                jdbc.update("UPDATE curso SET estado = 'PUBLICADO' WHERE id = ?", course.id());
                jdbc.update("MERGE INTO inscripcion(id, talento_id, curso_id) KEY(talento_id, curso_id) VALUES (?, ?, ?)",
                    UUID.randomUUID(), talent.usuarioId(), course.id());
            }
        }
    }

    @Test void membershipChangesInvalidateThePreviouslyResolvedSubjectImmediately() {
        var subject = createSubject("ADMIN");
        assertThat(policy.autorizar(subject, "oferta.gestionar", ResourceAccess.company(company))).isTrue();
        jdbc.update("UPDATE membresia SET rol = 'EDITOR' WHERE usuario_id = ?", subject.usuarioId());
        assertThat(policy.autorizar(subject, "oferta.gestionar", ResourceAccess.company(company))).isFalse();
        var editor = Sujeto.from(accounts.byId(subject.usuarioId()).orElseThrow());
        assertThat(policy.autorizar(editor, "curso.gestionar", ResourceAccess.company(company))).isTrue();
        jdbc.update("UPDATE membresia SET estado = 'BAJA' WHERE usuario_id = ?", subject.usuarioId());
        assertThat(policy.autorizar(editor, "curso.gestionar", ResourceAccess.company(company))).isFalse();
    }
}
