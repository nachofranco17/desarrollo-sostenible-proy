package uy.edu.um.xperience.course;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Respuestas de RF5. Las respuestas aceptadas nunca se incluyen: sólo cuántas hay (R2). */
public final class CourseView {
    private CourseView() {}

    public record Summary(UUID id, String tipo, String titulo, String tecnologia, String nivel,
                          String estado, int cantidadElementos, OffsetDateTime actualizadoEn) {}

    public record Detail(UUID id, String tipo, String titulo, String descripcion, String tecnologia,
                         String nivel, int duracionHoras, BigDecimal costo, String estado,
                         OffsetDateTime creadoEn, OffsetDateTime actualizadoEn,
                         List<Lesson> lecciones, List<Deliverable> entregables, List<Material> materiales) {
        static Detail of(Course c, List<Lesson> lecciones, List<Deliverable> entregables, List<Material> materiales) {
            return new Detail(c.id(), c.tipo(), c.titulo(), c.descripcion(), c.tecnologia(), c.nivel(),
                c.duracionHoras(), c.costo(), c.estado(), c.creadoEn(), c.actualizadoEn(),
                List.copyOf(lecciones), List.copyOf(entregables), List.copyOf(materiales));
        }
    }

    public record Lesson(UUID id, int numero, String titulo, String cuerpo) {}

    public record Deliverable(UUID id, int numero, String titulo, String consigna, String pista,
                              int cantidadRespuestasAceptadas) {}

    public record Material(UUID id, String nombre, String tipoMime, long tamanioBytes, OffsetDateTime subidoEn) {}
}
