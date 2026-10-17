package uy.edu.um.xperience.security;

import tools.jackson.core.*;
import tools.jackson.databind.*;

import java.util.Arrays;

/** Accept the existing JSON string format, reading Jackson's character buffer directly. */
public class PasswordDeserializer extends ValueDeserializer<char[]> {
    @Override public char[] deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            // Do not include the token's value in a binding error.
            throw DatabindException.from(parser, "La contraseña debe ser texto.");
        }
        char[] buffer = parser.getStringCharacters();
        int offset = parser.getStringOffset();
        return PasswordCleanupFilter.track(Arrays.copyOfRange(buffer, offset, offset + parser.getStringLength()));
    }
}
