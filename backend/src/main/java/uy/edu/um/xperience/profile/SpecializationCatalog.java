package uy.edu.um.xperience.profile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpecializationCatalog {
    public static final List<String> ALL = List.of(
        "Backend",
        "Frontend",
        "Full Stack",
        "React",
        "Seguridad",
        "Datos",
        "DevOps",
        "Mobile",
        "UX/UI",
        "Testing",
        "Cloud",
        "Gestión de proyectos",
        "Inteligencia Artificial"
    );

    private static final Set<String> ALLOWED = new LinkedHashSet<>(ALL);

    private SpecializationCatalog() {}

    public static boolean isAllowed(String value) {
        return value != null && ALLOWED.contains(value);
    }

    public static void requireAllValid(List<String> values) {
        if (values == null) {
            throw new ProfileValidationException("Las especializaciones deben enviarse como una lista.");
        }
        if (values.size() > ALL.size()) {
            throw new ProfileValidationException("Demasiadas especializaciones.");
        }
        for (String value : values) {
            if (!isAllowed(value)) {
                throw new ProfileValidationException(
                    "Especialización no válida. Elegí únicamente opciones del catálogo.");
            }
        }
        if (values.size() != Set.copyOf(values).size()) {
            throw new ProfileValidationException("Las especializaciones no pueden repetirse.");
        }
    }
}
