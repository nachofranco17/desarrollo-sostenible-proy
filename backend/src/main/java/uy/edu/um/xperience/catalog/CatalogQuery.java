package uy.edu.um.xperience.catalog;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Valida y normaliza los query params del catálogo (RF3). Solo reconoce la allowlist de filtros;
 * el resto de parámetros se ignoran y no alteran la consulta.
 */
public final class CatalogQuery {
    static final int MAX_NOMBRE = 150;
    static final int MAX_TECNOLOGIA = 60;
    private static final Set<String> NIVELES = Set.of("INICIAL", "INTERMEDIO", "AVANZADO");
    private static final BigDecimal COSTO_MAX = new BigDecimal("100000.00");

    private CatalogQuery() {}

    public static CatalogCriteria parse(String nombre, String tecnologia, String nivel,
                                        String duracionMin, String duracionMax,
                                        String costoMin, String costoMax) {
        String q = optionalText(nombre, MAX_NOMBRE, "La búsqueda por nombre no puede superar " + MAX_NOMBRE + " caracteres.");
        String tech = optionalText(tecnologia, MAX_TECNOLOGIA, "La tecnología no puede superar " + MAX_TECNOLOGIA + " caracteres.");
        String level = optionalNivel(nivel);
        Integer dMin = optionalInt(duracionMin, 1, 1000, "duración mínima");
        Integer dMax = optionalInt(duracionMax, 1, 1000, "duración máxima");
        if (dMin != null && dMax != null && dMin > dMax) {
            throw bad("La duración mínima no puede ser mayor que la máxima.");
        }
        BigDecimal cMin = optionalCost(costoMin, "costo mínimo");
        BigDecimal cMax = optionalCost(costoMax, "costo máximo");
        if (cMin != null && cMax != null && cMin.compareTo(cMax) > 0) {
            throw bad("El costo mínimo no puede ser mayor que el máximo.");
        }
        return new CatalogCriteria(q, tech, level, dMin, dMax, cMin, cMax);
    }

    private static String optionalText(String raw, int max, String tooLongMessage) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > max) {
            throw bad(tooLongMessage);
        }
        return value;
    }

    private static String optionalNivel(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!NIVELES.contains(upper)) {
            throw bad("Nivel inválido. Usá INICIAL, INTERMEDIO o AVANZADO.");
        }
        return upper;
    }

    private static Integer optionalInt(String raw, int min, int max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value < min || value > max) {
                throw bad("La " + label + " debe estar entre " + min + " y " + max + ".");
            }
            return value;
        } catch (NumberFormatException error) {
            throw bad("La " + label + " debe ser un número entero.");
        }
    }

    private static BigDecimal optionalCost(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.strip());
            if (value.scale() > 2) {
                throw bad("El " + label + " admite como máximo dos decimales.");
            }
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(COSTO_MAX) > 0) {
                throw bad("El " + label + " debe estar entre 0.00 y 100000.00.");
            }
            return value;
        } catch (NumberFormatException error) {
            throw bad("El " + label + " debe ser un número.");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
