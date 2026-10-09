package uy.edu.um.xperience.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Set;
import uy.edu.um.xperience.security.Denegaciones;

/**
 * Allowlist del PATCH de avance: sólo porcentajeAvance (entero 0–100).
 * El cliente no puede enviar ids, estado ni otros campos.
 */
public final class ProgressUpdateParser {
    static final Set<String> ALLOWED_FIELDS = Set.of("porcentajeAvance");

    private ProgressUpdateParser() {}

    public static int parse(JsonNode body) {
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
        if (!body.has("porcentajeAvance")) {
            throw new EnrollmentValidationException("El campo porcentajeAvance es obligatorio.");
        }
        JsonNode node = body.get("porcentajeAvance");
        if (node == null || node.isNull()) {
            throw new EnrollmentValidationException("El porcentaje de avance no puede ser null.");
        }
        if (!node.isNumber() || !node.isIntegralNumber()) {
            throw new EnrollmentValidationException(
                "El porcentaje de avance debe ser un número entero entre 0 y 100.");
        }
        int value;
        try {
            value = node.intValue();
            if (node.canConvertToLong() && node.longValue() != value) {
                throw new EnrollmentValidationException(
                    "El porcentaje de avance debe ser un número entero entre 0 y 100.");
            }
        } catch (ArithmeticException ex) {
            throw new EnrollmentValidationException(
                "El porcentaje de avance debe ser un número entero entre 0 y 100.");
        }
        // Rechaza NaN / infinitos que Jackson pudiera representar como número no finito.
        if (!Double.isFinite(node.doubleValue())) {
            throw new EnrollmentValidationException(
                "El porcentaje de avance debe ser un número entero entre 0 y 100.");
        }
        if (value < 0 || value > 100) {
            throw new EnrollmentValidationException(
                "El porcentaje de avance debe estar entre 0 y 100.");
        }
        return value;
    }
}
