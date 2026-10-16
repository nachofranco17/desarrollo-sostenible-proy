package uy.edu.um.xperience.profile;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import uy.edu.um.xperience.security.Denegaciones;
import uy.edu.um.xperience.security.PasswordDeserializer;
import uy.edu.um.xperience.security.Passwords;

/** Keep partial-update semantics without putting plaintext passwords in JsonNode/TextNode. */
public class ProfileUpdateDeserializer extends JsonDeserializer<ProfileUpdate> {
    @Override public ProfileUpdate getNullValue(DeserializationContext context) throws JsonMappingException {
        throw JsonMappingException.from(context.getParser(), "El cuerpo debe ser un objeto JSON.");
    }

    @Override public ProfileUpdate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            throw JsonMappingException.from(parser, "El cuerpo debe ser un objeto JSON.");
        }
        ObjectNode fields = context.getNodeFactory().objectNode();
        FieldChange<char[]> actual = FieldChange.absent();
        FieldChange<char[]> nueva = FieldChange.absent();
        boolean complete = false;
        try {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                    throw JsonMappingException.from(parser, "Se esperaba un campo del perfil.");
                }
                String field = parser.currentName();
                parser.nextToken();
                if (!ProfileUpdateParser.ALLOWED_FIELDS.contains(field)) {
                    Denegaciones.marcarCampoNoAutorizado(field);
                    throw JsonMappingException.from(parser, "El perfil contiene campos no permitidos.");
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

    private static FieldChange<char[]> readPassword(JsonParser parser, DeserializationContext context) throws IOException {
        char[] value = new PasswordDeserializer().deserialize(parser, context);
        if (value.length < 12 || value.length > 128) {
            Passwords.clear(value);
            throw JsonMappingException.from(parser, "La contraseña debe tener entre 12 y 128 caracteres.");
        }
        return FieldChange.of(value);
    }

    private static void clear(FieldChange<char[]> change) {
        if (change instanceof FieldChange.SetValue<char[]> value) Passwords.clear(value.value());
    }
}
