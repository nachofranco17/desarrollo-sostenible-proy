package uy.edu.um.xperience;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** V8 amplía el esquema de sprint 2 sin reescribir los datos ni permisos previos. */
class Rf6MigrationTest {
    @Test
    void upgradeFromV7PreservesAccountsProfilesCoursesMembershipsAndAll54Permissions() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:rf6-upgrade-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        UUID company = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        UUID talent = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        UUID lesson = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa(id, nombre) VALUES (?, 'Empresa previa')", company);
        for (var entry : Map.of(staff, "STAFF", talent, "TALENTO").entrySet()) {
            jdbc.update("""
                INSERT INTO usuario(id, correo, nombre, apellido, password_hash, tipo_cuenta)
                VALUES (?, ?, 'Nombre previo', 'Apellido previo', 'hash-previo', ?)
                """, entry.getKey(), entry.getKey() + "@example.test", entry.getValue());
        }
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, 'RECLUTADOR', 'ACTIVA')",
            staff, company);
        jdbc.update("""
            INSERT INTO perfil_talento(usuario_id, telefono, especializaciones, notificar_novedades_cursos, notificar_ofertas)
            VALUES (?, '+59899123456', '["Backend"]', FALSE, TRUE)
            """, talent);
        jdbc.update("""
            INSERT INTO curso(id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel,
                              duracion_horas, costo, estado, creado_por)
            VALUES (?, ?, 'CURSO', 'Curso previo', 'Descripcion previa', 'Java', 'INTERMEDIO', 20, 100, 'PUBLICADO', ?)
            """, course, company, staff);
        jdbc.update("INSERT INTO leccion(id, curso_id, empresa_id, numero, titulo, cuerpo) VALUES (?, ?, ?, 1, 'Leccion previa', 'Contenido')",
            lesson, course, company);
        List<Map<String, Object>> accounts = jdbc.queryForList("SELECT * FROM usuario ORDER BY id");
        List<Map<String, Object>> companies = jdbc.queryForList("SELECT * FROM empresa ORDER BY id");
        List<Map<String, Object>> memberships = jdbc.queryForList("SELECT * FROM membresia ORDER BY usuario_id");
        List<Map<String, Object>> profiles = jdbc.queryForList("SELECT * FROM perfil_talento ORDER BY usuario_id");
        List<Map<String, Object>> courses = jdbc.queryForList("SELECT * FROM curso ORDER BY id");
        List<Map<String, Object>> lessons = jdbc.queryForList("SELECT * FROM leccion ORDER BY id");
        List<Map<String, Object>> permissions = jdbc.queryForList("SELECT * FROM permiso ORDER BY rol, accion");
        assertThat(permissions).hasSize(54);

        var result = Flyway.configure().dataSource(dataSource).target("8").load().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM usuario ORDER BY id")).isEqualTo(accounts);
        assertThat(jdbc.queryForList("SELECT * FROM empresa ORDER BY id")).isEqualTo(companies);
        assertThat(jdbc.queryForList("SELECT * FROM membresia ORDER BY usuario_id")).isEqualTo(memberships);
        assertThat(jdbc.queryForList("""
            SELECT usuario_id, telefono, especializaciones, notificar_novedades_cursos, notificar_ofertas
            FROM perfil_talento ORDER BY usuario_id
            """)).isEqualTo(profiles);
        assertThat(jdbc.queryForList("SELECT * FROM curso ORDER BY id")).isEqualTo(courses);
        assertThat(jdbc.queryForList("SELECT * FROM leccion ORDER BY id")).isEqualTo(lessons);
        assertThat(jdbc.queryForList("SELECT * FROM permiso ORDER BY rol, accion")).isEqualTo(permissions);
        assertThat(jdbc.queryForObject("SELECT curriculum_actual_id FROM perfil_talento WHERE usuario_id = ?",
            UUID.class, talent)).isNull();
        for (String table : List.of("oferta", "oferta_requisito", "curriculum", "postulacion")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        }
        assertThat(Flyway.configure().dataSource(dataSource).target("8").load().validateWithResult().validationSuccessful)
            .isTrue();
    }
}
