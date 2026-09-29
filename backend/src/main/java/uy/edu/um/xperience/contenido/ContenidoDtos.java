package uy.edu.um.xperience.contenido;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Objetos de entrada y salida de la gestión de contenido. Los de escritura declaran sólo los campos
 * que la empresa puede modificar (empresa, estado, autor y tipo no están). Los de lectura nunca
 * incluyen las respuestas aceptadas, sólo cuántas hay.
 */
public final class ContenidoDtos {

    private ContenidoDtos() {
    }

    // ---- Escritura ----

    public record CrearContenido(
            @NotNull TipoContenido tipo,
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String descripcion,
            @NotBlank @Size(max = 60) String tecnologia,
            @NotNull Nivel nivel,
            @NotNull @Min(1) @Max(1000) Integer duracionHoras,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal costo) {

        Contenido.DatosContenido datos() {
            return new Contenido.DatosContenido(titulo, descripcion, tecnologia, nivel, duracionHoras, costo);
        }
    }

    public record ActualizarContenido(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String descripcion,
            @NotBlank @Size(max = 60) String tecnologia,
            @NotNull Nivel nivel,
            @NotNull @Min(1) @Max(1000) Integer duracionHoras,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal costo) {

        Contenido.DatosContenido datos() {
            return new Contenido.DatosContenido(titulo, descripcion, tecnologia, nivel, duracionHoras, costo);
        }
    }

    public record EscribirLeccion(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 20000) String cuerpo) {
    }

    public record CrearEntregable(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String consigna,
            @NotBlank @Size(max = 500) String pista,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 500) String> respuestasAceptadas) {
    }

    /** Si {@code respuestasAceptadas} viene, reemplaza todas las anteriores; si no, se conservan. */
    public record ActualizarEntregable(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String consigna,
            @NotBlank @Size(max = 500) String pista,
            @Size(min = 1, max = 20) List<@NotBlank @Size(max = 500) String> respuestasAceptadas) {
    }

    // ---- Lectura ----

    public record ResumenContenido(UUID id, TipoContenido tipo, String titulo, String tecnologia, Nivel nivel,
                                   EstadoContenido estado, int cantidadElementos, Instant actualizadoEn) {

        static ResumenContenido de(Contenido c) {
            return new ResumenContenido(c.getId(), c.getTipo(), c.getTitulo(), c.getTecnologia(), c.getNivel(),
                    c.getEstado(), c.cantidadElementos(), c.getActualizadoEn());
        }
    }

    public record DetalleContenido(UUID id, TipoContenido tipo, String titulo, String descripcion,
                                   String tecnologia, Nivel nivel, int duracionHoras, BigDecimal costo,
                                   EstadoContenido estado, Instant creadoEn, Instant actualizadoEn,
                                   List<VistaLeccion> lecciones, List<VistaEntregable> entregables,
                                   List<VistaMaterial> materiales) {

        static DetalleContenido de(Contenido c, List<Material> materiales) {
            return new DetalleContenido(c.getId(), c.getTipo(), c.getTitulo(), c.getDescripcion(),
                    c.getTecnologia(), c.getNivel(), c.getDuracionHoras(), c.getCosto(), c.getEstado(),
                    c.getCreadoEn(), c.getActualizadoEn(),
                    c.getLecciones().stream().map(VistaLeccion::de).toList(),
                    c.getEntregables().stream().map(VistaEntregable::de).toList(),
                    materiales.stream().map(VistaMaterial::de).toList());
        }
    }

    public record VistaLeccion(UUID id, int numero, String titulo, String cuerpo) {
        static VistaLeccion de(Leccion l) {
            return new VistaLeccion(l.getId(), l.getNumero(), l.getTitulo(), l.getCuerpo());
        }
    }

    public record VistaEntregable(UUID id, int numero, String titulo, String consigna, String pista,
                                  int cantidadRespuestasAceptadas) {
        static VistaEntregable de(Entregable e) {
            return new VistaEntregable(e.getId(), e.getNumero(), e.getTitulo(), e.getConsigna(), e.getPista(),
                    e.cantidadRespuestasAceptadas());
        }
    }

    public record VistaMaterial(UUID id, String nombre, String tipoMime, long tamanioBytes, Instant subidoEn) {
        static VistaMaterial de(Material m) {
            return new VistaMaterial(m.getId(), m.getNombreOriginal(), m.getTipoMime(), m.getTamanioBytes(),
                    m.getSubidoEn());
        }
    }
}
