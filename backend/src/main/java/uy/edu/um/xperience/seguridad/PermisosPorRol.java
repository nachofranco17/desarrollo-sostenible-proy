package uy.edu.um.xperience.seguridad;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mínimo privilegio por rol (R9). Cada rol recibe sólo las operaciones que necesita y ninguna otra;
 * no existe un rol con acceso total. Lo que no figura acá está denegado, incluido el staff sin rol.
 *
 * <ul>
 *   <li>El Reclutador no crea, modifica ni da de baja cursos, proyectos ni material.</li>
 *   <li>El Editor de contenido no publica ofertas ni ve postulantes.</li>
 *   <li>Invitar al staff y asignar roles es exclusivo del Administrador.</li>
 * </ul>
 *
 * Documentado en docs/seguridad/r9-permisos-por-rol.md.
 */
public final class PermisosPorRol {

    private static final Set<Accion> GESTION_CONTENIDO = EnumSet.of(
            Accion.CONTENIDO_GESTION_VER,
            Accion.CONTENIDO_CREAR,
            Accion.CONTENIDO_EDITAR,
            Accion.CONTENIDO_PUBLICAR,
            Accion.CONTENIDO_BAJAR,
            Accion.CONTENIDO_ELIMINAR,
            Accion.MATERIAL_SUBIR,
            Accion.MATERIAL_DESCARGAR,
            Accion.MATERIAL_ELIMINAR);

    private static final Set<Accion> RECLUTAMIENTO = EnumSet.of(
            Accion.OFERTA_GESTIONAR,
            Accion.POSTULANTES_VER);

    private static final Set<Accion> ADMINISTRACION_STAFF = EnumSet.of(
            Accion.STAFF_INVITAR,
            Accion.STAFF_ASIGNAR_ROL);

    private static final Map<Rol, Set<Accion>> MATRIZ = new EnumMap<>(Rol.class);

    static {
        MATRIZ.put(Rol.EMPRESA_ADMIN, union(GESTION_CONTENIDO, RECLUTAMIENTO, ADMINISTRACION_STAFF));
        MATRIZ.put(Rol.EMPRESA_EDITOR, GESTION_CONTENIDO);
        MATRIZ.put(Rol.EMPRESA_RECLUTADOR, RECLUTAMIENTO);
        // Las operaciones del Talento (catálogo, inscripción, postulación) se agregan con RF2, RF4 y RF6.
        MATRIZ.put(Rol.TALENTO, EnumSet.noneOf(Accion.class));
    }

    private PermisosPorRol() {
    }

    /** Operaciones del rol. Un rol nulo (staff sin rol asignado) no tiene ninguna. */
    public static Set<Accion> de(Rol rol) {
        return rol == null ? Set.of() : Collections.unmodifiableSet(MATRIZ.getOrDefault(rol, Set.of()));
    }

    public static boolean permite(Rol rol, Accion accion) {
        return de(rol).contains(accion);
    }

    /** Corta la operación con 403 si el rol del consumidor no incluye la acción. */
    public static void exigir(Consumidor consumidor, Accion accion) {
        if (!permite(consumidor.rol(), accion)) {
            throw new AccesoDenegadoException(accion);
        }
    }

    @SafeVarargs
    private static Set<Accion> union(Set<Accion>... conjuntos) {
        Set<Accion> resultado = EnumSet.noneOf(Accion.class);
        for (Set<Accion> conjunto : conjuntos) {
            resultado.addAll(conjunto);
        }
        return resultado;
    }
}
