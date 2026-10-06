package uy.edu.um.xperience.enrollment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import uy.edu.um.xperience.course.Course;

/**
 * Persistencia de inscripciones (RF4). Toda lectura por id se acota al talento de la sesión
 * (RS11): conocer el UUID no alcanza para ver o modificar la inscripción de otro.
 */
@Repository
public class EnrollmentRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<Enrollment> ENROLLMENT = (rs, row) -> new Enrollment(
        rs.getObject("id", UUID.class),
        rs.getObject("talento_id", UUID.class),
        rs.getObject("curso_id", UUID.class),
        rs.getObject("fecha", OffsetDateTime.class),
        rs.getInt("porcentaje_avance"));

    private static final RowMapper<EnrollmentView.Item> ITEM = (rs, row) -> new EnrollmentView.Item(
        rs.getObject("id", UUID.class),
        rs.getObject("curso_id", UUID.class),
        rs.getString("tipo"),
        rs.getString("titulo"),
        rs.getInt("porcentaje_avance"),
        rs.getObject("fecha", OffsetDateTime.class));

    private static final RowMapper<Course> COURSE = (rs, row) -> new Course(
        rs.getObject("id", UUID.class), rs.getObject("empresa_id", UUID.class), rs.getString("tipo"),
        rs.getString("titulo"), rs.getString("descripcion"), rs.getString("tecnologia"),
        rs.getString("nivel"), rs.getInt("duracion_horas"), rs.getBigDecimal("costo"),
        rs.getString("estado"), null, null);

    public EnrollmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Course> findPublishedCourse(UUID cursoId) {
        return jdbc.query("""
            SELECT id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel, duracion_horas, costo, estado
            FROM curso WHERE id = ? AND estado = 'PUBLICADO'
            """, COURSE, cursoId).stream().findFirst();
    }

    public boolean exists(UUID talentoId, UUID cursoId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM inscripcion WHERE talento_id = ? AND curso_id = ?",
            Integer.class, talentoId, cursoId);
        return count != null && count > 0;
    }

    public Enrollment insert(UUID talentoId, UUID cursoId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO inscripcion (id, talento_id, curso_id, porcentaje_avance)
            VALUES (?, ?, ?, 0)
            """, id, talentoId, cursoId);
        return findOwned(id, talentoId).orElseThrow();
    }

    public List<EnrollmentView.Item> listOwned(UUID talentoId) {
        return jdbc.query("""
            SELECT i.id, i.curso_id, c.tipo, c.titulo, i.porcentaje_avance, i.fecha
            FROM inscripcion i
            JOIN curso c ON c.id = i.curso_id
            WHERE i.talento_id = ?
            ORDER BY i.fecha DESC, i.id ASC
            """, ITEM, talentoId);
    }

    public Optional<Enrollment> findOwned(UUID id, UUID talentoId) {
        return jdbc.query("""
            SELECT id, talento_id, curso_id, fecha, porcentaje_avance
            FROM inscripcion WHERE id = ? AND talento_id = ?
            """, ENROLLMENT, id, talentoId).stream().findFirst();
    }

    /** Bloquea la fila propia para actualizar el avance de forma atómica. */
    public Optional<Enrollment> findOwnedForUpdate(UUID id, UUID talentoId) {
        return jdbc.query("""
            SELECT id, talento_id, curso_id, fecha, porcentaje_avance
            FROM inscripcion WHERE id = ? AND talento_id = ? FOR UPDATE
            """, ENROLLMENT, id, talentoId).stream().findFirst();
    }

    /** Lectura por id sin dueño: solo para denegar instancias ajenas sin revelar existencia vía otra vía. */
    public Optional<Enrollment> findById(UUID id) {
        return jdbc.query("""
            SELECT id, talento_id, curso_id, fecha, porcentaje_avance
            FROM inscripcion WHERE id = ?
            """, ENROLLMENT, id).stream().findFirst();
    }

    public Optional<EnrollmentView.Item> findOwnedItem(UUID id, UUID talentoId) {
        return jdbc.query("""
            SELECT i.id, i.curso_id, c.tipo, c.titulo, i.porcentaje_avance, i.fecha
            FROM inscripcion i
            JOIN curso c ON c.id = i.curso_id
            WHERE i.id = ? AND i.talento_id = ?
            """, ITEM, id, talentoId).stream().findFirst();
    }

    public void updateProgress(UUID id, UUID talentoId, int porcentajeAvance) {
        jdbc.update("""
            UPDATE inscripcion SET porcentaje_avance = ?
            WHERE id = ? AND talento_id = ?
            """, porcentajeAvance, id, talentoId);
    }
}
