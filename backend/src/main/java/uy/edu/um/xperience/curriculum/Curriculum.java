package uy.edu.um.xperience.curriculum;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Versión inmutable de un CV. La clave privada nunca forma parte de una respuesta HTTP. */
public record Curriculum(UUID id, UUID usuarioId, String nombreOriginal, String tipoMime,
                         long tamanioBytes, String claveAlmacen, OffsetDateTime creadoEn) {}
