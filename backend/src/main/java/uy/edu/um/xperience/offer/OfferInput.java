package uy.edu.um.xperience.offer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Solo campos editables: identidad, empresa, autor, fechas y estado los fija el servidor. */
public final class OfferInput {
    private OfferInput() {}

    public record Data(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String descripcion,
            @NotNull @Size(max = 100) List<@NotNull UUID> cursosRequeridos) {
        public Data {
            titulo = titulo == null ? null : titulo.strip();
            descripcion = descripcion == null ? null : descripcion.strip();
        }
    }

    /** Publicar no admite campos ni decisiones de estado suministradas por el cliente. */
    public record Publish() {}
}
