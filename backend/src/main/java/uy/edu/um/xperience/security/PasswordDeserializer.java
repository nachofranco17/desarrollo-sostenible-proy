package uy.edu.um.xperience.security;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.Arrays;

/** Accept the existing JSON string format, reading Jackson's character buffer directly. */
public class PasswordDeserializer extends JsonDeserializer<char[]> {
    @Override public char[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            // Do not include the token's value in a binding error.
            throw JsonMappingException.from(parser, "La contraseña debe ser texto.");
        }
        char[] buffer = parser.getTextCharacters();
        int offset = parser.getTextOffset();
        return PasswordCleanupFilter.track(Arrays.copyOfRange(buffer, offset, offset + parser.getTextLength()));
    }
}
