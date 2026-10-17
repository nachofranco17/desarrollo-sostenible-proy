package uy.edu.um.xperience.profile;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

import uy.edu.um.xperience.security.Denegaciones;
import uy.edu.um.xperience.security.PasswordDeserializer;
import uy.edu.um.xperience.security.Passwords;

/** Keep partial-update semantics without putting plaintext passwords in JsonNode/TextNode. */
public class ProfileUpdateDeserializer extends ValueDeserializer<ProfileUpdate> {
    @Override public ProfileUpdate getNullValue(DeserializationContext context) throws DatabindException {
        throw DatabindException.from(context.getParser(), "El cuerpo debe ser un objeto JSON.");
    }

    @Override public ProfileUpdate deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.isExpectedStartObjectToken()) {
            throw DatabindException.from(parser, "El cuerpo debe ser un objeto JSON.");
        }
        ObjectNode fields = context.getNodeFactory().objectNode();
        FieldChange<char[]> actual = FieldChange.absent();
        FieldChange<char[]> nueva = FieldChange.absent();
        boolean complete = false;
        try {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (!parser.hasToken(JsonToken.PROPERTY_NAME)) {
                    throw DatabindException.from(parser, "Se esperaba un campo del perfil.");
                }
                String field = parser.currentName();
                parser.nextToken();
                if (!ProfileUpdateParser.ALLOWED_FIELDS.contains(field)) {
                    Denegaciones.marcarCampoNoAutorizado(field);
                    throw DatabindException.from(parser, "El perfil contiene campos no permitidos.");
                }
                if (field.equals("passwordActual")) {
                    clear(actual);
                    actual = readPassword(parser, context);
                } else if (field.equals("passwordNueva")) {
                    clear(nueva);
                    nueva = readPassword(parser, context);
                } else {
                    fields.set(field, parser.readValueAsTree());
                }
            }
            ProfileUpdate result = ProfileUpdateParser.parse(fields, actual, nueva);
            complete = true;
            return result;
        } finally {
            if (!complete) { clear(actual); clear(nueva); }
        }
    }

    private static FieldChange<char[]> readPassword(JsonParser parser, DeserializationContext context) {
        char[] value = new PasswordDeserializer().deserialize(parser, context);
        if (value.length < 12 || value.length > 128) {
            Passwords.clear(value);
            throw DatabindException.from(parser, "La contraseña debe tener entre 12 y 128 caracteres.");
        }
        return FieldChange.of(value);
    }

    private static void clear(FieldChange<char[]> change) {
        if (change instanceof FieldChange.SetValue<char[]> value) Passwords.clear(value.value());
    }
}
