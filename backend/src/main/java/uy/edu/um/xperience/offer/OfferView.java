package uy.edu.um.xperience.offer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Proyecciones de negocio: no exponen el creador ni atributos internos de autorización. */
public final class OfferView {
    private OfferView() {}

    public record Summary(UUID id, String titulo, String estado, OffsetDateTime actualizadoEn) {}

    public record Detail(UUID id, String titulo, String descripcion, String estado,
                         OffsetDateTime creadoEn, OffsetDateTime actualizadoEn,
                         List<UUID> cursosRequeridos) {
        static Detail of(Offer offer, List<UUID> requirements) {
            return new Detail(offer.id(), offer.titulo(), offer.descripcion(), offer.estado(),
                offer.creadoEn(), offer.actualizadoEn(), List.copyOf(requirements));
        }
    }
}
