package uy.edu.um.xperience.course;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpos de escritura de RF5. Declaran sólo lo que la empresa puede modificar: empresa, estado,
 * autor e identificadores no están, y un campo extra rechaza la solicitud completa (R12,
 * spring.jackson.deserialization.fail-on-unknown-properties).
 */
public final class CourseInput {
    static final int MAX_RESPUESTAS = 10;

    private CourseInput() {}

    public record Create(
            @NotNull @Pattern(regexp = "CURSO|PROYECTO") String tipo,
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String descripcion,
            @NotBlank @Size(max = 60) String tecnologia,
            @NotNull @Pattern(regexp = "INICIAL|INTERMEDIO|AVANZADO") String nivel,
            @NotNull @Min(1) @Max(1000) Integer duracionHoras,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal costo) {
        Data data() { return new Data(titulo, descripcion, tecnologia, nivel, duracionHoras, costo); }
    }

    /** El tipo se fija al crear y no se modifica. */
    public record Data(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String descripcion,
            @NotBlank @Size(max = 60) String tecnologia,
            @NotNull @Pattern(regexp = "INICIAL|INTERMEDIO|AVANZADO") String nivel,
            @NotNull @Min(1) @Max(1000) Integer duracionHoras,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal costo) {
        public Data {
            titulo = strip(titulo);
            descripcion = strip(descripcion);
            tecnologia = strip(tecnologia);
        }
    }

    public record Lesson(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 20000) String cuerpo) {
        public Lesson { titulo = strip(titulo); }
    }

    public record NewDeliverable(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String consigna,
            @NotBlank @Size(max = 500) String pista,
            @NotEmpty @Size(max = MAX_RESPUESTAS) List<@NotBlank @Size(max = 500) String> respuestasAceptadas) {
        public NewDeliverable {
            titulo = strip(titulo);
            consigna = strip(consigna);
            pista = strip(pista);
        }
    }

    /** Si {@code respuestasAceptadas} viene, reemplaza todas las anteriores; si no, se conservan. */
    public record DeliverableChange(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 4000) String consigna,
            @NotBlank @Size(max = 500) String pista,
            @Size(min = 1, max = MAX_RESPUESTAS) List<@NotBlank @Size(max = 500) String> respuestasAceptadas) {
        public DeliverableChange {
            titulo = strip(titulo);
            consigna = strip(consigna);
            pista = strip(pista);
        }
    }

    private static String strip(String value) { return value == null ? null : value.strip(); }
}
