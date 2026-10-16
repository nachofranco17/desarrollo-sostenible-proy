package uy.edu.um.xperience.security;

/**
 * RS19 — codifica el texto antes de escribirlo en un registro (ASVS 16.4.1). Un salto de línea no puede
 * fabricar una entrada falsa y el contenido no puede ejecutarse en la herramienta con que se lean los
 * registros: los caracteres de control, de formato (p. ej. U+202E) y de marcado se escriben como
 * {@code \\uXXXX}. El resultado se acota para que un valor enorme no inunde el registro.
 */
public final class CodificadorRegistro {
    static final int MAX_CARACTERES = 512;
    private static final String MARCADO = "<>&\"'`";

    private CodificadorRegistro() {}

    public static String codificar(String value) {
        if (value == null) return null;
        int limit = Math.min(value.length(), MAX_CARACTERES);
        var out = new StringBuilder(limit + 16);
        for (int i = 0; i < limit; i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                    || Character.getType(c) == Character.LINE_SEPARATOR
                    || Character.getType(c) == Character.PARAGRAPH_SEPARATOR || MARCADO.indexOf(c) >= 0) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        if (value.length() > limit) out.append("...[truncado]");
        return out.toString();
    }
}
