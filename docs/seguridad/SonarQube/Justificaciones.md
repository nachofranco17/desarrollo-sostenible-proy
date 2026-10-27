Issue de SQL:

Es a propósito, porque la migración V4 reemplaza el catálogo completo de permisos (modelo-autorizacion.md v1.2) y reinserta todas las filas en el mismo script. No se modifica porque es una migración Flyway ya aplicada. Cambiarla altera su checksum y rompe el arranque de las bases existentes.

Los dos issues del content length:

Límite intencional. RF5 exige material de cursos (incluído MP4) de hasta 25 MB. Está mitigado porque el PEP rechaza en el filtro de seguridad las solicitudes sin sesión, CSRF o permiso material.subir antes de procesar el archivo