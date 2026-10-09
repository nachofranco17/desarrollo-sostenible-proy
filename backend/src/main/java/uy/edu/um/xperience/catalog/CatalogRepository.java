package uy.edu.um.xperience.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import uy.edu.um.xperience.course.Course;

/**
 * Consultas del catálogo público (RF3). Solo estado PUBLICADO; filtros con parámetros bind
 * (nunca concatenando el texto del usuario en el SQL).
 */
@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<CatalogView.Item> ITEM = (rs, row) -> new CatalogView.Item(
        rs.getObject("id", UUID.class), rs.getString("tipo"), rs.getString("titulo"),
        rs.getString("tecnologia"), rs.getString("nivel"), rs.getInt("duracion_horas"),
        rs.getBigDecimal("costo"), rs.getString("empresa_nombre"));

    private static final RowMapper<CatalogView.Detail> DETAIL = (rs, row) -> new CatalogView.Detail(
        rs.getObject("id", UUID.class), rs.getString("tipo"), rs.getString("titulo"),
        rs.getString("descripcion"), rs.getString("tecnologia"), rs.getString("nivel"),
        rs.getInt("duracion_horas"), rs.getBigDecimal("costo"), rs.getString("empresa_nombre"));

    private static final RowMapper<Course> COURSE = (rs, row) -> new Course(
        rs.getObject("id", UUID.class), rs.getObject("empresa_id", UUID.class), rs.getString("tipo"),
        rs.getString("titulo"), rs.getString("descripcion"), rs.getString("tecnologia"),
        rs.getString("nivel"), rs.getInt("duracion_horas"), rs.getBigDecimal("costo"),
        rs.getString("estado"), null, null);

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CatalogView.Item> search(CatalogCriteria criteria) {
        var sql = new StringBuilder("""
            SELECT c.id, c.tipo, c.titulo, c.tecnologia, c.nivel, c.duracion_horas, c.costo, e.nombre AS empresa_nombre
            FROM curso c JOIN empresa e ON e.id = c.empresa_id
            WHERE c.estado = 'PUBLICADO'
            """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, criteria);
        sql.append(" ORDER BY c.titulo ASC, c.id ASC");
        return jdbc.query(sql.toString(), ITEM, args.toArray());
    }

    public List<String> distinctTechnologies() {
        return jdbc.query("""
            SELECT DISTINCT c.tecnologia FROM curso c
            WHERE c.estado = 'PUBLICADO'
            ORDER BY c.tecnologia ASC
            """, (rs, row) -> rs.getString(1));
    }

    public Optional<CatalogView.Detail> findPublishedDetail(UUID id) {
        return jdbc.query("""
            SELECT c.id, c.tipo, c.titulo, c.descripcion, c.tecnologia, c.nivel, c.duracion_horas, c.costo,
                   e.nombre AS empresa_nombre
            FROM curso c JOIN empresa e ON e.id = c.empresa_id
            WHERE c.id = ? AND c.estado = 'PUBLICADO'
            """, DETAIL, id).stream().findFirst();
    }

    /** Curso publicado para autorización por instancia (PUBLICADO). */
    public Optional<Course> findPublishedCourse(UUID id) {
        return jdbc.query("""
            SELECT id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel, duracion_horas, costo, estado
            FROM curso WHERE id = ? AND estado = 'PUBLICADO'
            """, COURSE, id).stream().findFirst();
    }

    private static void appendFilters(StringBuilder sql, List<Object> args, CatalogCriteria c) {
        if (c.nombre() != null) {
            sql.append(" AND LOWER(c.titulo) LIKE LOWER(?) ESCAPE '\\'");
            args.add(likeContains(c.nombre()));
        }
        if (c.tecnologia() != null) {
            sql.append(" AND LOWER(c.tecnologia) = LOWER(?)");
            args.add(c.tecnologia());
        }
        if (c.nivel() != null) {
            sql.append(" AND c.nivel = ?");
            args.add(c.nivel());
        }
        if (c.duracionMin() != null) {
            sql.append(" AND c.duracion_horas >= ?");
            args.add(c.duracionMin());
        }
        if (c.duracionMax() != null) {
            sql.append(" AND c.duracion_horas <= ?");
            args.add(c.duracionMax());
        }
        if (c.costoMin() != null) {
            sql.append(" AND c.costo >= ?");
            args.add(c.costoMin());
        }
        if (c.costoMax() != null) {
            sql.append(" AND c.costo <= ?");
            args.add(c.costoMax());
        }
    }

    /** Escapa comodines de LIKE para que el texto del usuario sea literal. */
    static String likeContains(String value) {
        String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
