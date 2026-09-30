package uy.edu.um.xperience;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * R9 — Mínimo privilegio por rol. Cada rol solo puede tener las acciones que la matriz le asigna
 * (docs/seguridad/modelo-autorizacion.md, sección 3). Si alguien carga en la tabla permiso una
 * acción de más para un rol, esta prueba falla.
 */
@SpringBootTest
class LeastPrivilegeTest {
    /** Acciones de la matriz documentada; GET /api/account usa cuenta.gestionar. */
    private static final Map<String, Set<String>> MATRIZ = Map.of(
        "VISITANTE", Set.of("cuenta.registrar", "sesion.iniciar", "catalogo.ver"),
        "TALENTO", Set.of("sesion.cerrar", "cuenta.gestionar", "perfil.gestionar", "catalogo.ver",
            "material.descargar", "inscripcion.crear", "inscripcion.ver", "leccion.completar",
            "entregable.responder", "oferta.ver", "postulacion.crear", "postulacion.ver",
            "curriculum.subir", "curriculum.descargar"),
        "ADMIN", Set.of("sesion.cerrar", "sesion.reautenticar", "cuenta.gestionar",
            "perfil.ver_postulante", "catalogo.ver", "curso.gestionar", "curso.ver_borrador", "material.subir",
            "material.descargar", "inscripcion.ver_inscriptos", "oferta.ver", "oferta.gestionar",
            "postulacion.ver_listado", "postulacion.cambiar_estado", "curriculum.descargar",
            "staff.invitar", "staff.cambiar_rol", "staff.dar_baja", "staff.ver"),
        "RECLUTADOR", Set.of("sesion.cerrar", "cuenta.gestionar", "perfil.ver_postulante",
            "catalogo.ver", "oferta.ver", "oferta.gestionar", "postulacion.ver_listado",
            "postulacion.cambiar_estado", "curriculum.descargar"),
        "EDITOR", Set.of("sesion.cerrar", "cuenta.gestionar", "catalogo.ver", "curso.gestionar",
            "curso.ver_borrador", "material.subir", "material.descargar", "inscripcion.ver_inscriptos",
            "oferta.ver"));

    @Autowired JdbcTemplate jdbc;

    @Test
    void noRoleHasAnActionTheMatrixDoesNotGiveIt() {
        List<String> excess = jdbc.query("SELECT rol, accion FROM permiso ORDER BY rol, accion",
                (rs, row) -> rs.getString("rol") + " -> " + rs.getString("accion")).stream()
            .filter(row -> {
                String[] parts = row.split(" -> ");
                return !MATRIZ.getOrDefault(parts[0], Set.of()).contains(parts[1]);
            })
            .toList();
        assertThat(excess).as("Permisos que exceden la matriz de R1").isEmpty();
    }

    @Test
    void courseManagementBelongsOnlyToAdminAndEditor() {
        for (String action : List.of("curso.gestionar", "curso.ver_borrador", "material.subir")) {
            assertThat(rolesWith(action)).as(action).containsExactlyInAnyOrder("ADMIN", "EDITOR");
        }
        // Al Talento se le suma con RF4, con alcance INSCRIPTO; nunca al Reclutador.
        assertThat(rolesWith("material.descargar")).contains("ADMIN", "EDITOR").doesNotContain("RECLUTADOR");
    }

    @Test
    void recruiterCannotTouchContentAndEditorCannotTouchOffersOrStaff() {
        assertThat(actionsOf("RECLUTADOR")).noneMatch(a -> a.startsWith("curso.") || a.startsWith("material."));
        assertThat(actionsOf("EDITOR")).noneMatch(a -> a.equals("oferta.gestionar") || a.startsWith("postulacion.")
            || a.startsWith("staff."));
        for (String role : List.of("TALENTO", "RECLUTADOR", "EDITOR")) {
            assertThat(actionsOf(role)).as(role).noneMatch(a -> a.startsWith("staff."));
        }
    }

    private List<String> rolesWith(String action) {
        return jdbc.queryForList("SELECT rol FROM permiso WHERE accion = ?", String.class, action);
    }

    private List<String> actionsOf(String role) {
        return jdbc.queryForList("SELECT accion FROM permiso WHERE rol = ?", String.class, role);
    }
}
