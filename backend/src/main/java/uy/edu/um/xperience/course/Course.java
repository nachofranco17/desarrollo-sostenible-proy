package uy.edu.um.xperience.course;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Curso o proyecto de una empresa (RF5). El tipo decide si lleva lecciones o entregables. */
public record Course(UUID id, UUID empresaId, String tipo, String titulo, String descripcion,
                     String tecnologia, String nivel, int duracionHoras, BigDecimal costo,
                     String estado, OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {
    public static final String CURSO = "CURSO";
    public static final String PROYECTO = "PROYECTO";
    public static final String BORRADOR = "BORRADOR";
    public static final String PUBLICADO = "PUBLICADO";
    public static final String BAJADO = "BAJADO";

    public boolean isCurso() { return CURSO.equals(tipo); }
    public boolean isBorrador() { return BORRADOR.equals(estado); }
    public boolean isPublicado() { return PUBLICADO.equals(estado); }
    public boolean isBajado() { return BAJADO.equals(estado); }
}
