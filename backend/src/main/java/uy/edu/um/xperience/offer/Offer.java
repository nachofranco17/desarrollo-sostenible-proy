package uy.edu.um.xperience.offer;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Oferta laboral de una empresa. Los atributos de autorización provienen de la base de datos. */
public record Offer(UUID id, UUID empresaId, String titulo, String descripcion, String estado,
                    OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {
    public static final String BORRADOR = "BORRADOR";
    public static final String PUBLICADA = "PUBLICADA";

    public boolean isBorrador() { return BORRADOR.equals(estado); }
    public boolean isPublicado() { return PUBLICADA.equals(estado); }
}
