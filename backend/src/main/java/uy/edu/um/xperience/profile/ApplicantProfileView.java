package uy.edu.um.xperience.profile;

import java.util.List;

/** Proyección empresarial RF6; deliberadamente no contiene contacto telefónico ni preferencias. */
public record ApplicantProfileView(String nombre, String apellido, String correo,
                                   List<String> especializaciones) {
    public static ApplicantProfileView from(TalentProfile profile) {
        return new ApplicantProfileView(profile.nombre(), profile.apellido(), profile.correo(),
            List.copyOf(profile.especializaciones()));
    }
}
