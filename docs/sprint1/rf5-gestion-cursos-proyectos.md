# RF5 — Gestión de cursos y proyectos (y R9 — Mínimo privilegio por rol)

Implementamos el portal donde la empresa da de alta, modifica y da de baja sus cursos y proyectos, sobre la arquitectura que ya tenía el proyecto (sesión en cookie, permisos en base, JDBC y React). Junto con RF5 va R9: cada rol recibe solo las operaciones que le da la matriz.

## Qué quedó hecho

Desde `/mi-cuenta`, el Administrador y el Editor de contenido entran a `/gestion`. Ahí pueden:

- ver los cursos y proyectos de su empresa, con su estado;
- crear un curso (con lecciones numeradas) o un proyecto (con entregables);
- editar los datos generales: título, descripción, tecnología, nivel, duración y costo;
- agregar, editar y eliminar lecciones, y agregar, editar y eliminar entregables con su consigna, pista y respuestas aceptadas;
- subir, descargar y eliminar material (PDF, PNG, JPG, ZIP o MP4, hasta 25 MB);
- publicar, dar de baja y eliminar.

### Estados

| Operación | Borrador | Publicado | Dado de baja |
|---|---|---|---|
| Editar datos y agregar lecciones o entregables | sí | sí | no |
| Eliminar lecciones o entregables | sí | no | no |
| Subir o eliminar material | sí | sí | no |
| Publicar | sí¹ | — | sí¹ |
| Dar de baja | — | sí | — |
| Eliminar el curso o proyecto | sí | no | no |

¹ Un curso necesita al menos una lección y un proyecto al menos un entregable.

Una vez publicado ya no se borra ni se le quitan lecciones o entregables, porque puede haber Talentos avanzando en ellos. Al darlo de baja deja de admitir inscripciones, pero quienes ya estaban inscriptos conservan el acceso (decisión 9 de la matriz).

### Respuestas aceptadas

Se normalizan (saltos de línea unificados, espacios repetidos colapsados, recorte y minúsculas; ver `AnswerNormalizer`) y se guardan hasheadas con el `PasswordEncoder`, como indica `restricciones-campo.md`. Ninguna respuesta de la API las incluye: solo cuántas hay. Al editar un entregable, si no se mandan `respuestasAceptadas`, se conservan; si se mandan, reemplazan a todas. RF4 tiene que usar la misma función de normalización para comparar lo que envía el Talento.

## API

Todas las rutas están bajo `/api/company/courses`. La empresa sale siempre de la cuenta de la sesión: no viaja en ninguna ruta ni en ningún cuerpo.

| Método y ruta | Acción exigida | Resultado |
|---|---|---|
| `GET /` | `curso.ver_borrador` | Cursos y proyectos de la empresa |
| `GET /{id}` | `curso.ver_borrador` | Detalle con lecciones, entregables y material |
| `POST /` | `curso.gestionar` | 201, se crea en borrador |
| `PUT /{id}` | `curso.gestionar` | Datos generales |
| `DELETE /{id}` | `curso.gestionar` | 204, solo borradores |
| `POST /{id}/publish`, `POST /{id}/unpublish` | `curso.gestionar` | Publicar, dar de baja |
| `POST /{id}/lessons`, `PUT` y `DELETE /{id}/lessons/{lessonId}` | `curso.gestionar` | Lecciones |
| `POST /{id}/deliverables`, `PUT` y `DELETE /{id}/deliverables/{deliverableId}` | `curso.gestionar` | Entregables |
| `POST /{id}/materials` (multipart, campo `archivo`) | `material.subir` | Subir material |
| `GET /{id}/materials/{materialId}` | `material.descargar` | Descarga como adjunto |
| `DELETE /{id}/materials/{materialId}` | `curso.gestionar` | Eliminar material |

- Un curso de otra empresa responde igual que uno inexistente: 403.
- Lecciones, entregables y materiales se buscan dentro del curso, así que conocer su id no alcanza.
- Los cuerpos aceptan solo los campos del formulario. Un campo extra (`estado`, `empresaId`, etc.) da 400 y no se aplica nada.
- Las reglas de estado responden 409 con el motivo.
- El material se guarda con un nombre UUID en un directorio privado (`STORAGE_DIR`, por defecto `backend/.data/archivos`) y solo sale por el endpoint de descarga. El tipo se detecta por los primeros bytes del archivo, no por la extensión. Por ahora es disco local; el modelo prevé MinIO más adelante.

## R9 — Mínimo privilegio por rol

Las acciones de RF5 se cargan en la tabla `permiso` (migración `V4`) solo para `ADMIN` y `EDITOR`. El Reclutador y el Talento no reciben ninguna, así que `SecurityConfiguration` les responde 403 antes de llegar al controlador. La descarga de material para un Talento inscripto se agrega con RF4.

`LeastPrivilegeTest` compara la tabla `permiso` completa contra la sección 3 de `modelo-autorizacion.md`. Si alguien carga una acción de más para un rol, la prueba falla. Cuando se sumen acciones nuevas al catálogo, hay que agregarlas también ahí. La prueba incluye `cuenta.ver`, que no está en el catálogo: es la parte de lectura de `cuenta.gestionar` que usa `GET /api/account`.

## Base de datos

Migración `V4__cursos_proyectos.sql`: tablas `curso` (con `tipo` CURSO o PROYECTO), `leccion`, `entregable`, `respuesta_aceptada` y `material`. Todas las que dependen de una empresa llevan `empresa_id`.

## Pruebas

- `CourseIntegrationTest`: servidor real, sesión y CSRF. Cubre el ciclo de estados, la numeración, las respuestas hasheadas que no se devuelven, la subida y descarga de material (incluido uno de 2 MB), los tipos rechazados, los campos extra, el aislamiento entre empresas y, para R9, que el Reclutador y el Talento reciban 403 en las 13 operaciones sin que cambie nada en la base ni en el disco.
- `LeastPrivilegeTest`: la tabla de permisos contra la matriz.

## Pendiente o fuera de alcance

- Ver inscriptos y su avance (`inscripcion.ver_inscriptos`): depende de RF4.
- Registro de intentos denegados (R18).
- Pruebas E2E con Playwright para estas pantallas.
