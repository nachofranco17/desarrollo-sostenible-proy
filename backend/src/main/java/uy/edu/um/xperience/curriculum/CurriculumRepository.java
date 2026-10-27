package uy.edu.um.xperience.curriculum;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CurriculumRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Curriculum> mapper = (rs, row) -> new Curriculum(
        rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class),
        rs.getString("nombre_original"), rs.getString("tipo_mime"), rs.getLong("tamanio_bytes"),
        rs.getString("clave_almacen"), rs.getObject("creado_en", OffsetDateTime.class));

    public CurriculumRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** También lo usa la postulación: reemplazo y snapshot serializan sobre el mismo perfil. */
    public boolean lockProfile(UUID usuarioId) {
        return !jdbc.query("SELECT usuario_id FROM perfil_talento WHERE usuario_id = ? FOR UPDATE",
            (rs, row) -> rs.getObject("usuario_id", UUID.class), usuarioId).isEmpty();
    }

    public Optional<Curriculum> find(UUID id, UUID usuarioId) {
        return jdbc.query("SELECT * FROM curriculum WHERE id = ? AND usuario_id = ?", mapper,
            id, usuarioId).stream().findFirst();
    }

    public Optional<Curriculum> current(UUID usuarioId) {
        return jdbc.query("""
            SELECT c.* FROM curriculum c
            JOIN perfil_talento p ON p.curriculum_actual_id = c.id AND p.usuario_id = c.usuario_id
            WHERE p.usuario_id = ?
            """, mapper, usuarioId).stream().findFirst();
    }

    public void insert(Curriculum curriculum) {
        jdbc.update("""
            INSERT INTO curriculum(id, usuario_id, nombre_original, tipo_mime, tamanio_bytes,
                                   clave_almacen, creado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, curriculum.id(), curriculum.usuarioId(), curriculum.nombreOriginal(),
            curriculum.tipoMime(), curriculum.tamanioBytes(), curriculum.claveAlmacen(), curriculum.creadoEn());
    }

    public void setCurrent(UUID usuarioId, UUID curriculumId) {
        jdbc.update("UPDATE perfil_talento SET curriculum_actual_id = ? WHERE usuario_id = ?",
            curriculumId, usuarioId);
    }
}
