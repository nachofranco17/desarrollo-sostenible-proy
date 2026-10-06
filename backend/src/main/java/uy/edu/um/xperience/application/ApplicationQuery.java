package uy.edu.um.xperience.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.profile.SpecializationCatalog;
import uy.edu.um.xperience.security.Denegaciones;

public final class ApplicationQuery {
    private static final Set<String> FIELDS = Set.of("q", "especializacion", "fechaDesde", "fechaHasta");
    private ApplicationQuery() {}

    public static ApplicationCriteria parse(Map<String, String[]> parameters) {
        requireFields(parameters, FIELDS);
        String q = value(parameters, "q");
        if (q != null && (q.length() > 150 || q.codePoints().anyMatch(Character::isISOControl))) {
            throw invalid("La búsqueda admite hasta 150 caracteres.");
        }
        String specialization = value(parameters, "especializacion");
        if (specialization != null && !SpecializationCatalog.isAllowed(specialization)) {
            throw invalid("Especialización no válida.");
        }
        LocalDate from = date(value(parameters, "fechaDesde"));
        LocalDate to = date(value(parameters, "fechaHasta"));
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("La fecha desde no puede superar la fecha hasta.");
        }
        return new ApplicationCriteria(q, specialization, from, to);
    }

    public static void requireEmpty(Map<String, String[]> parameters) {
        requireFields(parameters, Set.of());
    }

    private static void requireFields(Map<String, String[]> parameters, Set<String> allowed) {
        for (var entry : parameters.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                Denegaciones.marcarCampoNoAutorizado(entry.getKey());
                throw invalid("Enviá únicamente los campos permitidos.");
            }
            if (entry.getValue().length != 1) throw invalid("No repitas los parámetros de filtro.");
        }
    }

    private static String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        if (values == null) return null;
        String value = values[0].strip();
        return value.isEmpty() ? null : value;
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("Usá fechas YYYY-MM-DD.");
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1 || date.getYear() > 9998) throw invalid("Fecha fuera de rango.");
            return date;
        } catch (DateTimeParseException error) { throw invalid("Fecha no válida."); }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
