package uy.edu.um.xperience.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.security.Denegaciones;

/**
 * Parsea el body del PATCH con allowlist explícita.
 * Distingue campo ausente / null / valor. Normaliza solo con trim; no “arregla” entradas inválidas.
 */
public final class ProfileUpdateParser {
    static final Set<String> ALLOWED_FIELDS = Set.of(
        "nombre", "apellido", "correo", "telefono", "especializaciones",
        "notificarNovedadesCursos", "notificarOfertas", "passwordActual", "passwordNueva"
    );

    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 80;
    private static final int EMAIL_MAX = 254;
    private static final int PHONE_MIN = 7;
    private static final int PHONE_MAX = 30;
    private static final int PASSWORD_MIN = 12;
    private static final int PASSWORD_MAX = 128;

    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^[\\p{L}](?:[\\p{L}\\p{M} ]*[\\p{L}\\p{M}])?$",
        Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+0-9() \\-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private ProfileUpdateParser() {}

    public static ProfileUpdate parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ProfileValidationException("El cuerpo debe ser un objeto JSON.");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                // RS18: solo el nombre técnico del campo; nunca el valor enviado.
                Denegaciones.marcarCampoNoAutorizado(field);
                throw new ProfileValidationException(
                    "Datos inválidos. Revisá los campos enviados e incluí únicamente los permitidos.");
            }
        }

        return new ProfileUpdate(
            parseName(body, "nombre"),
            parseName(body, "apellido"),
            parseEmail(body),
            parsePhone(body),
            parseSpecializations(body),
            parseBoolean(body, "notificarNovedadesCursos"),
            parseBoolean(body, "notificarOfertas"),
            parsePassword(body, "passwordActual"),
            parsePassword(body, "passwordNueva")
        );
    }

    private static FieldChange<String> parseName(JsonNode body, String field) {
        if (!body.has(field)) {
            return FieldChange.absent();
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            throw new ProfileValidationException("El campo " + field + " no puede ser null.");
        }
        if (!node.isTextual()) {
            throw new ProfileValidationException("El campo " + field + " debe ser texto.");
        }
        String value = node.asText().strip();
        if (value.isEmpty()) {
            throw new ProfileValidationException("El campo " + field + " es obligatorio.");
        }
        if (value.length() < NAME_MIN || value.length() > NAME_MAX) {
            throw new ProfileValidationException(
                "El campo " + field + " debe tener entre " + NAME_MIN + " y " + NAME_MAX + " caracteres.");
        }
        if (containsControlChars(value) || !NAME_PATTERN.matcher(value).matches()) {
            throw new ProfileValidationException(
                "El campo " + field + " solo puede tener letras y espacios (sin números ni símbolos).");
        }
        return FieldChange.of(value);
    }

    private static FieldChange<String> parseEmail(JsonNode body) {
        if (!body.has("correo")) {
            return FieldChange.absent();
        }
        JsonNode node = body.get("correo");
        if (node.isNull()) {
            throw new ProfileValidationException("El correo no puede ser null.");
        }
        if (!node.isTextual()) {
            throw new ProfileValidationException("El correo debe ser texto.");
        }
        String value = RegisterRequest.normalizeEmail(node.asText());
        if (value == null || value.isEmpty() || value.length() > EMAIL_MAX || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new ProfileValidationException("El correo tiene un formato inválido.");
        }
        return FieldChange.of(value);
    }

    private static FieldChange<String> parsePhone(JsonNode body) {
        if (!body.has("telefono")) {
            return FieldChange.absent();
        }
        JsonNode node = body.get("telefono");
        if (node.isNull()) {
            return FieldChange.clear();
        }
        if (!node.isTextual()) {
            throw new ProfileValidationException("El teléfono debe ser texto.");
        }
        String value = node.asText().strip();
        if (value.isEmpty()) {
            return FieldChange.clear();
        }
        // No se eliminan letras ni caracteres inválidos: se rechaza la entrada completa.
        if (!PHONE_PATTERN.matcher(value).matches()
                || value.chars().anyMatch(Character::isLetter)
                || value.length() < PHONE_MIN
                || value.length() > PHONE_MAX) {
            throw new ProfileValidationException(
                "El teléfono es inválido. Usá solo números, espacios, guiones, paréntesis o +.");
        }
        long digits = value.chars().filter(Character::isDigit).count();
        if (digits < 7) {
            throw new ProfileValidationException(
                "El teléfono es inválido. Usá solo números, espacios, guiones, paréntesis o +.");
        }
        return FieldChange.of(value);
    }

    private static FieldChange<List<String>> parseSpecializations(JsonNode body) {
        if (!body.has("especializaciones")) {
            return FieldChange.absent();
        }
        JsonNode node = body.get("especializaciones");
        if (node.isNull()) {
            throw new ProfileValidationException("Las especializaciones no pueden ser null; enviá una lista.");
        }
        if (!node.isArray()) {
            throw new ProfileValidationException("Las especializaciones deben ser una lista.");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new ProfileValidationException("Cada especialización debe ser texto.");
            }
            String value = item.asText().strip();
            if (value.isEmpty()) {
                throw new ProfileValidationException("Las especializaciones no pueden estar vacías.");
            }
            values.add(value);
        }
        SpecializationCatalog.requireAllValid(values);
        return FieldChange.of(List.copyOf(values));
    }

    private static FieldChange<Boolean> parseBoolean(JsonNode body, String field) {
        if (!body.has(field)) {
            return FieldChange.absent();
        }
        JsonNode node = body.get(field);
        if (node.isNull() || !node.isBoolean()) {
            throw new ProfileValidationException("El campo " + field + " debe ser un booleano (true/false).");
        }
        return FieldChange.of(node.booleanValue());
    }

    private static FieldChange<String> parsePassword(JsonNode body, String field) {
        if (!body.has(field)) {
            return FieldChange.absent();
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            throw new ProfileValidationException("El campo " + field + " no puede ser null.");
        }
        if (!node.isTextual()) {
            throw new ProfileValidationException("El campo " + field + " debe ser texto.");
        }
        String value = node.asText();
        if (value.length() < PASSWORD_MIN || value.length() > PASSWORD_MAX) {
            throw new ProfileValidationException(
                "La contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.");
        }
        return FieldChange.of(value);
    }

    private static boolean containsControlChars(String value) {
        return value.codePoints().anyMatch(cp -> {
            int type = Character.getType(cp);
            return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.PRIVATE_USE || type == Character.SURROGATE
                || cp == 0x7F;
        });
    }
}
