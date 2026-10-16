package uy.edu.um.xperience.profile;

import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uy.edu.um.xperience.security.Passwords;

@JsonDeserialize(using = ProfileUpdateDeserializer.class)
public record ProfileUpdate(
        FieldChange<String> nombre,
        FieldChange<String> apellido,
        FieldChange<String> correo,
        FieldChange<String> telefono,
        FieldChange<List<String>> especializaciones,
        FieldChange<Boolean> notificarNovedadesCursos,
        FieldChange<Boolean> notificarOfertas,
        FieldChange<char[]> passwordActual,
        FieldChange<char[]> passwordNueva) implements AutoCloseable {

    @Override public void close() {
        if (passwordActual instanceof FieldChange.SetValue<char[]> actual) Passwords.clear(actual.value());
        if (passwordNueva instanceof FieldChange.SetValue<char[]> nueva) Passwords.clear(nueva.value());
    }

    @Override public String toString() { return "ProfileUpdate[redacted]"; }

    boolean wantsPasswordChange() {
        return passwordActual.isPresent() || passwordNueva.isPresent();
    }

    boolean hasCompletePasswordChange() {
        return passwordActual instanceof FieldChange.SetValue<?>
            && passwordNueva instanceof FieldChange.SetValue<?>;
    }
}
