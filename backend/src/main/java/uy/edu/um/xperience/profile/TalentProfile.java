package uy.edu.um.xperience.profile;

import java.util.List;
import java.util.UUID;

public record TalentProfile(
        UUID usuarioId,
        String correo,
        String nombre,
        String apellido,
        String telefono,
        List<String> especializaciones,
        boolean notificarNovedadesCursos,
        boolean notificarOfertas) {
}
