package uy.edu.um.xperience.profile;

import java.util.List;

public record ProfileView(
        String nombre,
        String apellido,
        String correo,
        String telefono,
        List<String> especializaciones,
        List<String> especializacionesDisponibles,
        boolean notificarNovedadesCursos,
        boolean notificarOfertas,
        List<CourseProgressItem> progresoCursos,
        List<CompletedProjectItem> proyectosCompletados,
        List<AchievementItem> logros) {

    public static ProfileView from(TalentProfile profile,
                                   List<CourseProgressItem> progreso,
                                   List<CompletedProjectItem> proyectos,
                                   List<AchievementItem> logros) {
        return new ProfileView(
            profile.nombre(),
            profile.apellido(),
            profile.correo(),
            profile.telefono(),
            List.copyOf(profile.especializaciones()),
            SpecializationCatalog.ALL,
            profile.notificarNovedadesCursos(),
            profile.notificarOfertas(),
            List.copyOf(progreso),
            List.copyOf(proyectos),
            List.copyOf(logros));
    }

    public record CourseProgressItem(String nombre, int porcentajeAvance, String estado) {}
    public record CompletedProjectItem(String nombre, String fechaCompletado) {}
    public record AchievementItem(String titulo, String origen) {}
}
