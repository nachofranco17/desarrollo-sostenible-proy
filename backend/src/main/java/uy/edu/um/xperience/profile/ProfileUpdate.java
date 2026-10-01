package uy.edu.um.xperience.profile;

import java.util.List;

public record ProfileUpdate(
        FieldChange<String> nombre,
        FieldChange<String> apellido,
        FieldChange<String> correo,
        FieldChange<String> telefono,
        FieldChange<List<String>> especializaciones,
        FieldChange<Boolean> notificarNovedadesCursos,
        FieldChange<Boolean> notificarOfertas,
        FieldChange<String> passwordActual,
        FieldChange<String> passwordNueva) {

    boolean wantsPasswordChange() {
        return passwordActual.isPresent() || passwordNueva.isPresent();
    }

    boolean hasCompletePasswordChange() {
        return passwordActual instanceof FieldChange.SetValue<?>
            && passwordNueva instanceof FieldChange.SetValue<?>;
    }
}
