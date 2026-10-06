package uy.edu.um.xperience;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Las restricciones RF6 también rechazan escrituras que omiten la capa de servicios. */
class Rf6DatabaseConstraintsTest {
    private JdbcTemplate jdbc;
    private UUID companyA;
    private UUID companyB;
    private UUID author;
    private UUID talentA;
    private UUID talentB;
    private UUID offer;
    private UUID curriculumA;

    @BeforeEach
    void schemaAndFixtures() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:rf6-constraints-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).target("8").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        companyA = company();
        companyB = company();
        author = user("STAFF");
        talentA = user("TALENTO");
        talentB = user("TALENTO");
        jdbc.update("INSERT INTO perfil_talento(usuario_id) VALUES (?), (?)", talentA, talentB);
        offer = offer(companyA);
        curriculumA = curriculum(talentA, "application/pdf", 12);
    }

    @Test
    void oneApplicationPerOfferAndTalentIsUniqueInTheDatabase() {
        UUID saved = apply(talentA, offer, companyA, curriculumA);
        assertThatThrownBy(() -> apply(talentA, offer, companyA, curriculumA))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM postulacion", UUID.class)).isEqualTo(saved);
    }

    @Test
    void applicationMustReferenceTheActualOfferCompanyAndCurriculumOwner() {
        assertThatThrownBy(() -> apply(talentA, offer, companyB, curriculumA))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> apply(talentB, offer, companyA, curriculumA))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> apply(talentA, offer, companyA, UUID.randomUUID()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> apply(author, offer, companyA, curriculumA))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion", Integer.class)).isZero();
        UUID curriculumB = curriculum(talentB, "application/pdf", 15);
        apply(talentB, offer, companyA, curriculumB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion", Integer.class)).isEqualTo(1);
    }

    @Test
    void currentCurriculumPointerCannotReferenceAnotherTalentsVersion() {
        jdbc.update("UPDATE perfil_talento SET curriculum_actual_id = ? WHERE usuario_id = ?", curriculumA, talentA);
        assertThatThrownBy(() -> jdbc.update("UPDATE perfil_talento SET curriculum_actual_id = ? WHERE usuario_id = ?",
            curriculumA, talentB)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT curriculum_actual_id FROM perfil_talento WHERE usuario_id = ?",
            UUID.class, talentB)).isNull();
        assertThat(jdbc.queryForObject("SELECT curriculum_actual_id FROM perfil_talento WHERE usuario_id = ?",
            UUID.class, talentA)).isEqualTo(curriculumA);
    }

    @Test
    void onlySentApplicationsAndDraftOrPublishedOffersArePersistable() {
        UUID application = apply(talentA, offer, companyA, curriculumA);
        for (String state : new String[]{"PRESELECCIONADA", "SELECCIONADA", "DESCARTADA", ""}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE postulacion SET estado = ? WHERE id = ?", state, application))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE oferta SET estado = 'CERRADA' WHERE id = ?", offer))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT estado FROM postulacion WHERE id = ?", String.class, application))
            .isEqualTo("ENVIADA");
        assertThat(jdbc.queryForObject("SELECT estado FROM oferta WHERE id = ?", String.class, offer))
            .isEqualTo("PUBLICADA");
        assertThatThrownBy(() -> jdbc.update("UPDATE oferta SET titulo = '   ' WHERE id = ?", offer))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE oferta SET descripcion = '' WHERE id = ?", offer))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void curriculumMustBePdfAndItsSizeMustBeBetweenOneByteAndFiveMiB() {
        for (String mime : new String[]{"text/plain", "image/png", "application/octet-stream"}) {
            assertThatThrownBy(() -> curriculum(talentA, mime, 20)).isInstanceOf(DataIntegrityViolationException.class);
        }
        for (long size : new long[]{-1, 0, 5 * 1024 * 1024L + 1}) {
            assertThatThrownBy(() -> curriculum(talentA, "application/pdf", size))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
        curriculum(talentA, "application/pdf", 1);
        curriculum(talentA, "application/pdf", 5 * 1024 * 1024L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curriculum", Integer.class)).isEqualTo(3);
    }

    @Test
    void requirementsAreUniqueAndBelongToTheOfferCompanyWhileCoursesMayBeFromAnotherCompany() {
        UUID course = course(companyB);
        jdbc.update("INSERT INTO oferta_requisito(oferta_id, empresa_id, curso_id) VALUES (?, ?, ?)", offer, companyA, course);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO oferta_requisito(oferta_id, empresa_id, curso_id) VALUES (?, ?, ?)",
            offer, companyA, course)).isInstanceOf(DataIntegrityViolationException.class);
        UUID anotherCourse = course(companyB);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO oferta_requisito(oferta_id, empresa_id, curso_id) VALUES (?, ?, ?)",
            offer, companyB, anotherCourse)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta_requisito", Integer.class)).isEqualTo(1);
    }

    @Test
    void deletingReferencedOffersCurriculaOrCoursesCannotCascadeAwayApplicationHistory() {
        UUID application = apply(talentA, offer, companyA, curriculumA);
        UUID course = course(companyB);
        jdbc.update("INSERT INTO oferta_requisito(oferta_id, empresa_id, curso_id) VALUES (?, ?, ?)", offer, companyA, course);
        UUID replacement = curriculum(talentA, "application/pdf", 30);
        jdbc.update("UPDATE perfil_talento SET curriculum_actual_id = ? WHERE usuario_id = ?", replacement, talentA);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM oferta WHERE id = ?", offer))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM curriculum WHERE id = ?", curriculumA))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM curriculum WHERE id = ?", replacement))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM curso WHERE id = ?", course))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT curriculum_id FROM postulacion WHERE id = ?", UUID.class, application))
            .isEqualTo(curriculumA);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta_requisito WHERE oferta_id = ?", Integer.class, offer))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curriculum", Integer.class)).isEqualTo(2);
    }

    private UUID company() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa(id, nombre) VALUES (?, 'Empresa')", id);
        return id;
    }

    private UUID user(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO usuario(id, correo, nombre, apellido, password_hash, tipo_cuenta)
            VALUES (?, ?, 'Nombre', 'Apellido', 'test-hash', ?)
            """, id, id + "@example.test", type);
        return id;
    }

    private UUID offer(UUID company) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO oferta(id, empresa_id, titulo, descripcion, estado, creado_por)
            VALUES (?, ?, 'Oferta', 'Descripcion', 'PUBLICADA', ?)
            """, id, company, author);
        return id;
    }

    private UUID curriculum(UUID owner, String mime, long size) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO curriculum(id, usuario_id, nombre_original, tipo_mime, tamanio_bytes, clave_almacen)
            VALUES (?, ?, 'curriculum.pdf', ?, ?, ?)
            """, id, owner, mime, size, UUID.randomUUID().toString());
        return id;
    }

    private UUID apply(UUID talent, UUID offerId, UUID company, UUID curriculum) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO postulacion(id, talento_id, oferta_id, empresa_id, curriculum_id) VALUES (?, ?, ?, ?, ?)",
            id, talent, offerId, company, curriculum);
        return id;
    }

    private UUID course(UUID company) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO curso(id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel,
                              duracion_horas, costo, estado, creado_por)
            VALUES (?, ?, 'CURSO', 'Curso', 'Descripcion', 'Java', 'INICIAL', 10, 0, 'PUBLICADO', ?)
            """, id, company, author);
        return id;
    }
}
