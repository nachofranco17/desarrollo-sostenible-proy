package uy.edu.um.xperience.course;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Toda consulta lleva la empresa, tomada de la sesión (R15): un curso de otra empresa y uno
 * inexistente dan el mismo resultado. Lecciones, entregables y materiales se buscan también por
 * curso, así que conocer su id no alcanza para llegar a ellos (R11, R13).
 */
@Repository
public class CourseRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<Course> COURSE = (rs, row) -> new Course(
        rs.getObject("id", UUID.class), rs.getObject("empresa_id", UUID.class), rs.getString("tipo"),
        rs.getString("titulo"), rs.getString("descripcion"), rs.getString("tecnologia"),
        rs.getString("nivel"), rs.getInt("duracion_horas"), rs.getBigDecimal("costo"),
        rs.getString("estado"), rs.getObject("creado_en", OffsetDateTime.class),
        rs.getObject("actualizado_en", OffsetDateTime.class));
    private static final RowMapper<CourseView.Lesson> LESSON = (rs, row) -> new CourseView.Lesson(
        rs.getObject("id", UUID.class), rs.getInt("numero"), rs.getString("titulo"), rs.getString("cuerpo"));
    private static final RowMapper<CourseView.Deliverable> DELIVERABLE = (rs, row) -> new CourseView.Deliverable(
        rs.getObject("id", UUID.class), rs.getInt("numero"), rs.getString("titulo"),
        rs.getString("consigna"), rs.getString("pista"), rs.getInt("respuestas"));
    private static final RowMapper<StoredMaterial> MATERIAL = (rs, row) -> new StoredMaterial(
        rs.getObject("id", UUID.class), rs.getString("nombre_original"), rs.getString("tipo_mime"),
        rs.getLong("tamanio_bytes"), rs.getString("clave_almacen"),
        rs.getObject("subido_en", OffsetDateTime.class));

    /** Material con su ubicación en el almacenamiento, que nunca sale del backend. */
    public record StoredMaterial(UUID id, String nombre, String tipoMime, long tamanioBytes,
                                 String claveAlmacen, OffsetDateTime subidoEn) {
        CourseView.Material view() { return new CourseView.Material(id, nombre, tipoMime, tamanioBytes, subidoEn); }
    }

    public CourseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---- Curso ----

    public List<CourseView.Summary> list(UUID empresaId) {
        return jdbc.query("""
            SELECT c.id, c.tipo, c.titulo, c.tecnologia, c.nivel, c.estado, c.actualizado_en,
                   CASE WHEN c.tipo = 'CURSO'
                        THEN (SELECT COUNT(*) FROM leccion l WHERE l.curso_id = c.id)
                        ELSE (SELECT COUNT(*) FROM entregable e WHERE e.curso_id = c.id) END AS elementos
            FROM curso c WHERE c.empresa_id = ? ORDER BY c.actualizado_en DESC
            """, (rs, row) -> new CourseView.Summary(rs.getObject("id", UUID.class), rs.getString("tipo"),
                rs.getString("titulo"), rs.getString("tecnologia"), rs.getString("nivel"), rs.getString("estado"),
                rs.getInt("elementos"), rs.getObject("actualizado_en", OffsetDateTime.class)), empresaId);
    }

    public Optional<Course> find(UUID id, UUID empresaId) {
        return jdbc.query("SELECT * FROM curso WHERE id = ? AND empresa_id = ?", COURSE, id, empresaId)
            .stream().findFirst();
    }

    /** Bloquea la fila para que dos cambios concurrentes sobre el mismo curso no se pisen. */
    public Optional<Course> findForUpdate(UUID id, UUID empresaId) {
        return jdbc.query("SELECT * FROM curso WHERE id = ? AND empresa_id = ? FOR UPDATE", COURSE, id, empresaId)
            .stream().findFirst();
    }

    public UUID create(UUID empresaId, UUID autor, String tipo, CourseInput.Data d) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO curso (id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel,
                               duracion_horas, costo, estado, creado_por)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'BORRADOR', ?)
            """, id, empresaId, tipo, d.titulo(), d.descripcion(), d.tecnologia(), d.nivel(),
            d.duracionHoras(), d.costo(), autor);
        return id;
    }

    public void update(UUID id, UUID empresaId, CourseInput.Data d) {
        jdbc.update("""
            UPDATE curso SET titulo = ?, descripcion = ?, tecnologia = ?, nivel = ?, duracion_horas = ?,
                             costo = ?, actualizado_en = CURRENT_TIMESTAMP
            WHERE id = ? AND empresa_id = ?
            """, d.titulo(), d.descripcion(), d.tecnologia(), d.nivel(), d.duracionHoras(), d.costo(),
            id, empresaId);
    }

    public void changeState(UUID id, UUID empresaId, String estado) {
        jdbc.update("UPDATE curso SET estado = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ? AND empresa_id = ?",
            estado, id, empresaId);
    }

    public void touch(UUID id, UUID empresaId) {
        jdbc.update("UPDATE curso SET actualizado_en = CURRENT_TIMESTAMP WHERE id = ? AND empresa_id = ?", id, empresaId);
    }

    public void delete(UUID id, UUID empresaId) {
        jdbc.update("DELETE FROM curso WHERE id = ? AND empresa_id = ?", id, empresaId);
    }

    // ---- Lecciones ----

    public List<CourseView.Lesson> lessons(UUID cursoId, UUID empresaId) {
        return jdbc.query("SELECT * FROM leccion WHERE curso_id = ? AND empresa_id = ? ORDER BY numero",
            LESSON, cursoId, empresaId);
    }

    public void addLesson(UUID cursoId, UUID empresaId, CourseInput.Lesson l) {
        jdbc.update("""
            INSERT INTO leccion (id, curso_id, empresa_id, numero, titulo, cuerpo)
            VALUES (?, ?, ?, (SELECT COALESCE(MAX(numero), 0) + 1 FROM leccion WHERE curso_id = ?), ?, ?)
            """, UUID.randomUUID(), cursoId, empresaId, cursoId, l.titulo(), l.cuerpo());
    }

    public boolean updateLesson(UUID id, UUID cursoId, UUID empresaId, CourseInput.Lesson l) {
        return jdbc.update("UPDATE leccion SET titulo = ?, cuerpo = ? WHERE id = ? AND curso_id = ? AND empresa_id = ?",
            l.titulo(), l.cuerpo(), id, cursoId, empresaId) == 1;
    }

    public boolean deleteLesson(UUID id, UUID cursoId, UUID empresaId) {
        Integer numero = jdbc.query("SELECT numero FROM leccion WHERE id = ? AND curso_id = ? AND empresa_id = ?",
            (rs, row) -> rs.getInt(1), id, cursoId, empresaId).stream().findFirst().orElse(null);
        if (numero == null) {
            return false;
        }
        jdbc.update("DELETE FROM leccion WHERE id = ?", id);
        jdbc.update("UPDATE leccion SET numero = numero - 1 WHERE curso_id = ? AND numero > ?", cursoId, numero);
        return true;
    }

    public int countLessons(UUID cursoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM leccion WHERE curso_id = ?", Integer.class, cursoId);
    }

    // ---- Entregables ----

    public List<CourseView.Deliverable> deliverables(UUID cursoId, UUID empresaId) {
        return jdbc.query("""
            SELECT e.*, (SELECT COUNT(*) FROM respuesta_aceptada r WHERE r.entregable_id = e.id) AS respuestas
            FROM entregable e WHERE e.curso_id = ? AND e.empresa_id = ? ORDER BY e.numero
            """, DELIVERABLE, cursoId, empresaId);
    }

    public UUID addDeliverable(UUID cursoId, UUID empresaId, String titulo, String consigna, String pista) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO entregable (id, curso_id, empresa_id, numero, titulo, consigna, pista)
            VALUES (?, ?, ?, (SELECT COALESCE(MAX(numero), 0) + 1 FROM entregable WHERE curso_id = ?), ?, ?, ?)
            """, id, cursoId, empresaId, cursoId, titulo, consigna, pista);
        return id;
    }

    public boolean updateDeliverable(UUID id, UUID cursoId, UUID empresaId, String titulo, String consigna, String pista) {
        return jdbc.update("""
            UPDATE entregable SET titulo = ?, consigna = ?, pista = ?
            WHERE id = ? AND curso_id = ? AND empresa_id = ?
            """, titulo, consigna, pista, id, cursoId, empresaId) == 1;
    }

    public boolean deleteDeliverable(UUID id, UUID cursoId, UUID empresaId) {
        Integer numero = jdbc.query("SELECT numero FROM entregable WHERE id = ? AND curso_id = ? AND empresa_id = ?",
            (rs, row) -> rs.getInt(1), id, cursoId, empresaId).stream().findFirst().orElse(null);
        if (numero == null) {
            return false;
        }
        jdbc.update("DELETE FROM entregable WHERE id = ?", id);
        jdbc.update("UPDATE entregable SET numero = numero - 1 WHERE curso_id = ? AND numero > ?", cursoId, numero);
        return true;
    }

    public int countDeliverables(UUID cursoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM entregable WHERE curso_id = ?", Integer.class, cursoId);
    }

    /** Las respuestas se reemplazan en bloque; no hay consulta que las devuelva. */
    public void replaceAnswers(UUID entregableId, List<String> hashes) {
        jdbc.update("DELETE FROM respuesta_aceptada WHERE entregable_id = ?", entregableId);
        for (String hash : hashes) {
            jdbc.update("INSERT INTO respuesta_aceptada (id, entregable_id, hash) VALUES (?, ?, ?)",
                UUID.randomUUID(), entregableId, hash);
        }
    }

    // ---- Material ----

    public List<StoredMaterial> materials(UUID cursoId, UUID empresaId) {
        return jdbc.query("SELECT * FROM material WHERE curso_id = ? AND empresa_id = ? ORDER BY subido_en, id",
            MATERIAL, cursoId, empresaId);
    }

    public Optional<StoredMaterial> material(UUID id, UUID cursoId, UUID empresaId) {
        return jdbc.query("SELECT * FROM material WHERE id = ? AND curso_id = ? AND empresa_id = ?",
            MATERIAL, id, cursoId, empresaId).stream().findFirst();
    }

    public int countMaterials(UUID cursoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE curso_id = ?", Integer.class, cursoId);
    }

    public void addMaterial(UUID cursoId, UUID empresaId, String nombre, String tipoMime, long tamanio,
                            String clave, UUID autor) {
        jdbc.update("""
            INSERT INTO material (id, curso_id, empresa_id, nombre_original, tipo_mime, tamanio_bytes, clave_almacen, subido_por)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), cursoId, empresaId, nombre, tipoMime, tamanio, clave, autor);
    }

    public void deleteMaterial(UUID id, UUID cursoId, UUID empresaId) {
        jdbc.update("DELETE FROM material WHERE id = ? AND curso_id = ? AND empresa_id = ?", id, cursoId, empresaId);
    }
}
