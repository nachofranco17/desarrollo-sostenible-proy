package uy.edu.um.xperience.offer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Consultas parametrizadas y acotadas por empresa o publicación antes de devolver datos. */
@Repository
public class OfferRepository {
    private static final RowMapper<Offer> OFFER = (rs, row) -> new Offer(
        rs.getObject("id", UUID.class), rs.getObject("empresa_id", UUID.class),
        rs.getString("titulo"), rs.getString("descripcion"), rs.getString("estado"),
        rs.getObject("creado_en", OffsetDateTime.class),
        rs.getObject("actualizado_en", OffsetDateTime.class));
    private static final RowMapper<OfferView.Summary> SUMMARY = (rs, row) -> new OfferView.Summary(
        rs.getObject("id", UUID.class), rs.getString("titulo"), rs.getString("estado"),
        rs.getObject("actualizado_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public OfferRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<OfferView.Summary> list(UUID companyId) {
        return jdbc.query("""
            SELECT id, titulo, estado, actualizado_en FROM oferta WHERE empresa_id = ?
            ORDER BY actualizado_en DESC, id
            """, SUMMARY, companyId);
    }

    public List<OfferView.Summary> listPublished() {
        return jdbc.query("""
            SELECT id, titulo, estado, actualizado_en FROM oferta WHERE estado = 'PUBLICADA'
            ORDER BY actualizado_en DESC, id
            """, SUMMARY);
    }

    public Optional<Offer> find(UUID id, UUID companyId) {
        return jdbc.query("SELECT * FROM oferta WHERE id = ? AND empresa_id = ?", OFFER, id, companyId)
            .stream().findFirst();
    }

    public Optional<Offer> findForUpdate(UUID id, UUID companyId) {
        return jdbc.query("SELECT * FROM oferta WHERE id = ? AND empresa_id = ? FOR UPDATE", OFFER, id, companyId)
            .stream().findFirst();
    }

    public Optional<Offer> findPublished(UUID id) {
        return jdbc.query("SELECT * FROM oferta WHERE id = ? AND estado = 'PUBLICADA'", OFFER, id)
            .stream().findFirst();
    }

    public Optional<Offer> findPublishedForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM oferta WHERE id = ? AND estado = 'PUBLICADA' FOR UPDATE", OFFER, id)
            .stream().findFirst();
    }

    public UUID create(UUID companyId, UUID author, OfferInput.Data input) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO oferta (id, empresa_id, titulo, descripcion, estado, creado_por)
            VALUES (?, ?, ?, ?, 'BORRADOR', ?)
            """, id, companyId, input.titulo(), input.descripcion(), author);
        return id;
    }

    public void update(UUID id, UUID companyId, OfferInput.Data input) {
        jdbc.update("""
            UPDATE oferta SET titulo = ?, descripcion = ?, actualizado_en = CURRENT_TIMESTAMP
            WHERE id = ? AND empresa_id = ? AND estado = 'BORRADOR'
            """, input.titulo(), input.descripcion(), id, companyId);
    }

    public void publish(UUID id, UUID companyId) {
        jdbc.update("""
            UPDATE oferta SET estado = 'PUBLICADA', actualizado_en = CURRENT_TIMESTAMP
            WHERE id = ? AND empresa_id = ? AND estado = 'BORRADOR'
            """, id, companyId);
    }

    public List<UUID> requirements(UUID id, UUID companyId) {
        return jdbc.query("""
            SELECT curso_id FROM oferta_requisito WHERE oferta_id = ? AND empresa_id = ? ORDER BY curso_id
            """, (rs, row) -> rs.getObject("curso_id", UUID.class), id, companyId);
    }

    public void replaceRequirements(UUID id, UUID companyId, List<UUID> requirements) {
        jdbc.update("DELETE FROM oferta_requisito WHERE oferta_id = ? AND empresa_id = ?", id, companyId);
        for (UUID courseId : requirements) {
            jdbc.update("INSERT INTO oferta_requisito (oferta_id, empresa_id, curso_id) VALUES (?, ?, ?)",
                id, companyId, courseId);
        }
    }

    /** Cursos de cualquier empresa, bloqueados hasta confirmar la escritura de la oferta. */
    public boolean isPublishedRequirementForUpdate(UUID courseId) {
        return !jdbc.query("SELECT id FROM curso WHERE id = ? AND estado = 'PUBLICADO' FOR UPDATE",
            (rs, row) -> rs.getObject("id", UUID.class), courseId).isEmpty();
    }
}
