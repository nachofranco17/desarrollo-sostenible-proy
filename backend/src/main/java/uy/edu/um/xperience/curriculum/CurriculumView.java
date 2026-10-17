package uy.edu.um.xperience.curriculum;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Metadatos públicos del archivo propio, sin dueño ni ubicación del almacenamiento. */
public record CurriculumView(UUID id, String nombreOriginal, String tipoMime,
                             long tamanioBytes, OffsetDateTime creadoEn) {
    public static CurriculumView from(Curriculum curriculum) {
        return new CurriculumView(curriculum.id(), curriculum.nombreOriginal(), curriculum.tipoMime(),
            curriculum.tamanioBytes(), curriculum.creadoEn());
    }
}
