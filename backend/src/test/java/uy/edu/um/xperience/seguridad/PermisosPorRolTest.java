package uy.edu.um.xperience.seguridad;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R9: cada rol tiene exactamente las operaciones de la matriz, y ninguna otra. */
class PermisosPorRolTest {

    private static final Set<Accion> GESTION_CONTENIDO = EnumSet.of(
            Accion.CONTENIDO_GESTION_VER, Accion.CONTENIDO_CREAR, Accion.CONTENIDO_EDITAR,
            Accion.CONTENIDO_PUBLICAR, Accion.CONTENIDO_BAJAR, Accion.CONTENIDO_ELIMINAR,
            Accion.MATERIAL_SUBIR, Accion.MATERIAL_DESCARGAR, Accion.MATERIAL_ELIMINAR);

    @Test
    void elEditorSoloGestionaContenido() {
        assertThat(PermisosPorRol.de(Rol.EMPRESA_EDITOR)).containsExactlyInAnyOrderElementsOf(GESTION_CONTENIDO);
    }

    @Test
    void elEditorNoPublicaOfertasNiVePostulantes() {
        assertThat(PermisosPorRol.permite(Rol.EMPRESA_EDITOR, Accion.OFERTA_GESTIONAR)).isFalse();
        assertThat(PermisosPorRol.permite(Rol.EMPRESA_EDITOR, Accion.POSTULANTES_VER)).isFalse();
    }

    @Test
    void elReclutadorNoCreaModificaNiDaDeBajaContenidoNiMaterial() {
        assertThat(PermisosPorRol.de(Rol.EMPRESA_RECLUTADOR)).doesNotContainAnyElementsOf(GESTION_CONTENIDO);
        assertThat(PermisosPorRol.de(Rol.EMPRESA_RECLUTADOR))
                .containsExactlyInAnyOrder(Accion.OFERTA_GESTIONAR, Accion.POSTULANTES_VER);
    }

    @Test
    void soloElAdministradorInvitaAlStaffYAsignaRoles() {
        for (Rol rol : Rol.values()) {
            boolean esAdmin = rol == Rol.EMPRESA_ADMIN;
            assertThat(PermisosPorRol.permite(rol, Accion.STAFF_INVITAR)).as(rol.name()).isEqualTo(esAdmin);
            assertThat(PermisosPorRol.permite(rol, Accion.STAFF_ASIGNAR_ROL)).as(rol.name()).isEqualTo(esAdmin);
        }
    }

    @Test
    void elAdministradorGestionaContenidoYReclutamiento() {
        assertThat(PermisosPorRol.de(Rol.EMPRESA_ADMIN))
                .containsAll(GESTION_CONTENIDO)
                .contains(Accion.OFERTA_GESTIONAR, Accion.POSTULANTES_VER);
    }

    @Test
    void elTalentoNoTieneOperacionesDeEmpresa() {
        assertThat(PermisosPorRol.de(Rol.TALENTO)).isEmpty();
    }

    @Test
    void elStaffSinRolNoTieneNingunPermiso() {
        assertThat(PermisosPorRol.de(null)).isEmpty();
        assertThatThrownBy(() -> PermisosPorRol.exigir(new Consumidor(UUID.randomUUID(), null, UUID.randomUUID()),
                Accion.CONTENIDO_GESTION_VER))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void laMatrizNoSePuedeModificarDesdeAfuera() {
        assertThatThrownBy(() -> PermisosPorRol.de(Rol.EMPRESA_RECLUTADOR).add(Accion.CONTENIDO_CREAR))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
