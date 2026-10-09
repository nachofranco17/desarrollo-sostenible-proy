package uy.edu.um.xperience.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import uy.edu.um.xperience.security.Denegaciones;

/** Allowlist del POST de inscripción: sólo cursoId. */
public final class EnrollmentCreateParser {
    static final Set<String> ALLOWED_FIELDS = Set.of("cursoId");

    private EnrollmentCreateParser() {}

    public static UUID parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new EnrollmentValidationException("El cuerpo debe ser un objeto JSON.");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                Denegaciones.marcarCampoNoAutorizado(field);
                throw new EnrollmentValidationException(
                    "Datos inválidos. Revisá los campos enviados e incluí únicamente los permitidos.");
            }
        }
        if (!body.has("cursoId")) {
            throw new EnrollmentValidationException("El campo cursoId es obligatorio.");
        }
        JsonNode node = body.get("cursoId");
        if (node == null || node.isNull()) {
            throw new EnrollmentValidationException("El campo cursoId no puede ser null.");
        }
        if (!node.isTextual()) {
            throw new EnrollmentValidationException("El campo cursoId debe ser un UUID en texto.");
        }
        try {
            return UUID.fromString(node.asText().strip());
        } catch (IllegalArgumentException ex) {
            throw new EnrollmentValidationException("El campo cursoId tiene un formato inválido.");
        }
    }
}
