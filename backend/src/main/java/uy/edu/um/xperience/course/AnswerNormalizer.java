package uy.edu.um.xperience.course;

import java.util.Locale;

/**
 * Normalización de respuestas de entregables (docs/seguridad/restricciones-campo.md). Se aplica igual
 * a las respuestas aceptadas que carga la empresa (RF5) y a la que envía el Talento (RF4): saltos de
 * línea unificados, espacios repetidos colapsados, recorte en los extremos y minúsculas.
 */
public final class AnswerNormalizer {
    private AnswerNormalizer() {}

    public static String normalize(String answer) {
        return answer.replace("\r\n", "\n").replace('\r', '\n')
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" ?\n ?", "\n")
            .strip()
            .toLowerCase(Locale.ROOT);
    }
}
