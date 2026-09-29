package uy.edu.um.xperience.contenido;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Las respuestas aceptadas de un entregable se guardan normalizadas y hasheadas; el backend compara
 * contra ese hash la respuesta que envía el Talento (RF4, RF5).
 */
@Component
public class HasherRespuestas {

    /** Mayúsculas, espacios repetidos y formas Unicode equivalentes no cambian la respuesta. */
    public static String normalizar(String respuesta) {
        return Normalizer.normalize(respuesta, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public String hashear(String respuesta) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(normalizar(respuesta).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
