package uy.edu.um.xperience.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import uy.edu.um.xperience.offer.Offer;

/** Todas las consultas fijan dueño o empresa/oferta antes de aplicar filtros del cliente. */
@Repository
public class ApplicationRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<JobApplication> APPLICATION = (rs, row) -> new JobApplication(
        rs.getObject("id", UUID.class), rs.getObject("talento_id", UUID.class),
        rs.getObject("oferta_id", UUID.class), rs.getObject("empresa_id", UUID.class),
        rs.getObject("curriculum_id", UUID.class), rs.getString("estado"),
        rs.getObject("fecha", OffsetDateTime.class));
    private static final RowMapper<ApplicationView.Own> OWN = (rs, row) -> new ApplicationView.Own(
        rs.getObject("id", UUID.class), rs.getObject("oferta_id", UUID.class),
        rs.getString("oferta_titulo"), rs.getString("empresa_nombre"), rs.getString("estado"),
        rs.getObject("fecha", OffsetDateTime.class));

    public ApplicationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean exists(UUID offer, UUID talent) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM postulacion WHERE oferta_id = ? AND talento_id = ?",
            Integer.class, offer, talent) > 0;
    }

    public UUID create(UUID talent, Offer offer, UUID curriculum) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO postulacion(id, talento_id, oferta_id, empresa_id, curriculum_id, estado)
            VALUES (?, ?, ?, ?, ?, 'ENVIADA')
            """, id, talent, offer.id(), offer.empresaId(), curriculum);
        return id;
    }

    public Optional<JobApplication> own(UUID id, UUID talent) {
        return jdbc.query("SELECT * FROM postulacion WHERE id = ? AND talento_id = ?", APPLICATION, id, talent)
            .stream().findFirst();
    }

    public List<ApplicationView.Own> ownList(UUID talent) {
        return jdbc.query("""
            SELECT p.id, p.oferta_id, o.titulo AS oferta_titulo, e.nombre AS empresa_nombre, p.estado, p.fecha
            FROM postulacion p JOIN oferta o ON o.id = p.oferta_id AND o.empresa_id = p.empresa_id
            JOIN empresa e ON e.id = p.empresa_id
            WHERE p.talento_id = ? ORDER BY p.fecha DESC, p.id
            """, OWN, talent);
    }

    public Optional<ApplicationView.Own> ownView(UUID id, UUID talent) {
        return jdbc.query("""
            SELECT p.id, p.oferta_id, o.titulo AS oferta_titulo, e.nombre AS empresa_nombre, p.estado, p.fecha
            FROM postulacion p JOIN oferta o ON o.id = p.oferta_id AND o.empresa_id = p.empresa_id
            JOIN empresa e ON e.id = p.empresa_id
            WHERE p.id = ? AND p.talento_id = ?
            """, OWN, id, talent).stream().findFirst();
    }

    public Optional<JobApplication> companyApplication(UUID id, UUID offer, UUID company) {
        return jdbc.query("SELECT * FROM postulacion WHERE id = ? AND oferta_id = ? AND empresa_id = ?",
            APPLICATION, id, offer, company).stream().findFirst();
    }

    public List<JobApplication> companyList(UUID offer, UUID company, ApplicationCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.* FROM postulacion p JOIN usuario u ON u.id = p.talento_id
            WHERE p.empresa_id = ? AND p.oferta_id = ?
            """);
        List<Object> args = new ArrayList<>(List.of(company, offer));
        if (criteria.q() != null) {
            sql.append(" AND LOWER(CONCAT(u.nombre, ' ', u.apellido)) LIKE LOWER(?) ESCAPE '\\'");
            args.add("%" + criteria.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (criteria.fechaDesde() != null) {
            sql.append(" AND p.fecha >= ?");
            args.add(criteria.fechaDesde().atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        if (criteria.fechaHasta() != null) {
            sql.append(" AND p.fecha < ?");
            args.add(criteria.fechaHasta().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        sql.append(" ORDER BY p.fecha DESC, p.id");
        return jdbc.query(sql.toString(), APPLICATION, args.toArray());
    }
}
