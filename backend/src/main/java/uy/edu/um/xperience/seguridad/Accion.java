package uy.edu.um.xperience.seguridad;

/**
 * Operaciones que se asignan a los roles en {@link PermisosPorRol}. Incluye las de RF5 y las que R9
 * menciona explícitamente para ofertas y staff, que implementarán RF6 y RF7.
 */
public enum Accion {
    // RF5: gestión de cursos y proyectos
    CONTENIDO_GESTION_VER,
    CONTENIDO_CREAR,
    CONTENIDO_EDITAR,
    CONTENIDO_PUBLICAR,
    CONTENIDO_BAJAR,
    CONTENIDO_ELIMINAR,
    MATERIAL_SUBIR,
    MATERIAL_DESCARGAR,
    MATERIAL_ELIMINAR,

    // RF6: ofertas y postulantes
    OFERTA_GESTIONAR,
    POSTULANTES_VER,

    // RF7: staff de la empresa
    STAFF_INVITAR,
    STAFF_ASIGNAR_ROL
}
