# Matriz de control de acceso

Requerimiento de seguridad: **RS1** (R1, ASVS v5.0.0-8.1.1)
Versión: 1.3 (03/11/2026)

Este documento define, para cada tipo de consumidor, qué operaciones puede invocar y sobre qué conjunto de datos. Toda decisión de autorización de la plataforma debe poder contrastarse contra esta matriz. Es además la base para cargar los permisos del modelo de autorización (R4, ver [modelo-autorizacion.md](modelo-autorizacion.md)), para los conjuntos de operaciones por rol (R9) y para los casos de prueba de autorización (R20).

Las restricciones a nivel de campo (qué campos puede leer o modificar cada consumidor) se definen en [restricciones-campo.md](restricciones-campo.md) (R2).

## Consumidores

| Consumidor | Cómo se crea la cuenta |
|---|---|
| **Visitante** | No autenticado. |
| **Talento** | Registro desde la aplicación. Recibe su conjunto base de permisos al registrarse. |
| **Admin de empresa** | Lo da de alta el equipo de desarrollo junto con la empresa, fuera de la aplicación. |
| **Reclutador** | Invitado por el Admin de su empresa. Queda sin rol hasta que el Admin se lo asigna (R7). |
| **Editor de contenido** | Invitado por el Admin de su empresa. Queda sin rol hasta que el Admin se lo asigna (R7). |

## Matriz

Cada fila corresponde a una combinación de método HTTP y endpoint implementado, con una decisión para cada uno de los cinco consumidores. La matriz incluye las rutas procesadas por filtros de Spring Security y los casos especiales del PEP. Los permisos corresponden a las migraciones versionadas; no incluyen cambios manuales en una base de demostración. Las operaciones previstas que todavía no tienen endpoint se detallan por separado.

| Recurso | Operación | Visitante | Talento | Admin Empresa | Reclutador | Editor |
|---|---|---|---|---|---|---|
| Cuenta | Registrarse como Talento: `POST /api/auth/register` | ✓ | — | — | — | — |
| Sesión | Iniciar sesión: `POST /api/auth/login` | ✓ | — | — | — | — |
| Sesión | Cerrar la propia sesión: `POST /api/auth/logout` | — | propia | propia | propia | propia |
| Sesión | Reautenticarse con la contraseña de la propia cuenta: `POST /api/auth/reauthenticate` | — | — | propia | — | — |
| Sesión | Obtener el token CSRF de la propia sesión: `GET /api/auth/csrf` | propia | propia | propia | propia | propia |
| Cuenta | Ver la propia cuenta: `GET /api/account` | — | propia | propia | propia | propia |
| Cuenta | Eliminar la propia cuenta (baja lógica): `DELETE /api/account` | — | propia | propia | propia | propia |
| Perfil del Talento | Ver el propio perfil: `GET /api/profile/me` | — | propio | — | — | — |
| Perfil del Talento | Modificar el propio perfil: `PATCH /api/profile/me` | — | propio | — | — | — |
| Catálogo | Buscar cursos/proyectos publicados: `GET /api/catalog/courses` | ✓ | ✓ | ✓ | ✓ | ✓ |
| Catálogo | Ver un curso/proyecto publicado: `GET /api/catalog/courses/{id}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| Curso/proyecto | Listar los de la empresa, incluidos borradores: `GET /api/company/courses` | — | — | org | — | org |
| Curso/proyecto | Crear: `POST /api/company/courses` | — | — | org | — | org |
| Curso/proyecto | Ver el detalle, incluidos borradores: `GET /api/company/courses/{id}` | — | — | org | — | org |
| Curso/proyecto | Modificar: `PUT /api/company/courses/{id}` | — | — | org | — | org |
| Curso/proyecto | Eliminar: `DELETE /api/company/courses/{id}` | — | — | org | — | org |
| Curso/proyecto | Publicar: `POST /api/company/courses/{id}/publish` | — | — | org | — | org |
| Curso/proyecto | Dar de baja un publicado: `POST /api/company/courses/{id}/unpublish` | — | — | org | — | org |
| Lección | Agregar: `POST /api/company/courses/{id}/lessons` | — | — | org | — | org |
| Lección | Modificar: `PUT /api/company/courses/{id}/lessons/{lessonId}` | — | — | org | — | org |
| Lección | Eliminar: `DELETE /api/company/courses/{id}/lessons/{lessonId}` | — | — | org | — | org |
| Entregable | Agregar: `POST /api/company/courses/{id}/deliverables` | — | — | org | — | org |
| Entregable | Modificar: `PUT /api/company/courses/{id}/deliverables/{deliverableId}` | — | — | org | — | org |
| Entregable | Eliminar: `DELETE /api/company/courses/{id}/deliverables/{deliverableId}` | — | — | org | — | org |
| Material | Subir: `POST /api/company/courses/{id}/materials` | — | — | org | — | org |
| Material | Descargar: `GET /api/company/courses/{id}/materials/{materialId}` | — | inscripto | org | — | org |
| Material | Eliminar: `DELETE /api/company/courses/{id}/materials/{materialId}` | — | — | org | — | org |
| Inscripción | Inscribirse a un curso/proyecto publicado: `POST /api/enrollments` | — | ✓ | — | — | — |
| Inscripción | Listar las propias y su avance: `GET /api/enrollments/me` | — | propias | — | — | — |
| Inscripción | Ver la propia y su avance: `GET /api/enrollments/{id}` | — | propia | — | — | — |
| Inscripción | Actualizar el porcentaje de avance: `PATCH /api/enrollments/{id}/progress` | — | propia | — | — | — |
| Oferta | Listar publicadas: `GET /api/offers` | — | ✓ | ✓ | ✓ | ✓ |
| Oferta | Ver una publicada: `GET /api/offers/{id}` | — | ✓ | ✓ | ✓ | ✓ |
| Oferta | Listar las de la empresa, incluidos borradores: `GET /api/company/offers` | — | — | org | org | — |
| Oferta | Crear un borrador: `POST /api/company/offers` | — | — | org | org | — |
| Oferta | Ver el detalle, incluidos borradores: `GET /api/company/offers/{id}` | — | — | org | org | — |
| Oferta | Modificar un borrador: `PUT /api/company/offers/{id}` | — | — | org | org | — |
| Oferta | Publicar un borrador: `POST /api/company/offers/{id}/publish` | — | — | org | org | — |
| Postulación | Postularse a una oferta publicada: `POST /api/offers/{id}/applications` | — | ✓ | — | — | — |
| Postulación | Listar las propias y su estado: `GET /api/applications/me` | — | propias | — | — | — |
| Postulación | Ver el listado de una oferta de la empresa: `GET /api/company/offers/{id}/applications` | — | — | org | org | — |
| Perfil del Talento | Ver el perfil de un postulante (campos de R2): `GET /api/company/offers/{id}/applications/{applicationId}/profile` | — | — | postul. | postul. | — |
| Currículum | Subir o reemplazar el propio: `POST /api/profile/me/curriculum` | — | propio | — | — | — |
| Currículum | Consultar metadatos del propio: `GET /api/profile/me/curriculum/metadata` | — | propio | — | — | — |
| Currículum | Descargar el propio: `GET /api/profile/me/curriculum` | — | propio | — | — | — |
| Currículum | Descargar el presentado en una postulación: `GET /api/company/offers/{id}/applications/{applicationId}/curriculum` | — | — | postul. | postul. | — |
| Staff | Ver miembros y su rol: `GET /api/staff` | — | — | org | — | — |
| Staff | Invitar: `POST /api/staff/invitations` | — | — | org | — | — |
| Staff | Cambiar el rol de otro miembro: `PATCH /api/staff/{memberId}/rol` | — | — | org (+R23) | — | — |
| Staff | Dar de baja a otro miembro: `POST /api/staff/{memberId}/baja` | — | — | org (+R23) | — | — |
| Invitación | Aceptar mediante un token válido: `POST /api/invitations/accept` | token | token | token | token | token |
| Demo | Probar la acción `demo.permiso_asignable`: `GET /api/demo/permiso-asignable` | — | — | — | — | — |

### Alcances

| Símbolo | Significado |
|---|---|
| **✓** | Cualquier instancia que cumpla la condición de la operación (por ejemplo, estar publicada), sin importar a qué usuario o empresa pertenezca. |
| **propio / propia / propias** | Solo las instancias del propio usuario (R11). En las operaciones de sesión, solo la propia sesión, incluida la sesión anónima del Visitante para obtener CSRF. |
| **org** | Solo recursos de su empresa. El `empresa_id` se toma siempre de la sesión, nunca de la solicitud (R15). |
| **postul.** | Solo el perfil y la versión del CV asociados a una postulación persistida en estado `ENVIADA`, a la oferta indicada y a la empresa de la sesión. El servidor verifica esa relación; un identificador por sí solo no concede acceso. |
| **inscripto** | Solo material, lecciones y entregables de cursos o proyectos a los que el Talento está inscripto, aunque luego hayan sido dados de baja. |
| **+R23** | Requiere reautenticación antes de ejecutar la operación. |
| **token** | Solo con un token de invitación válido, vigente y no utilizado, enviado en `X-Invitation-Token`. La autorización depende del token, no del rol ni de tener sesión. |
| **—** | Denegado. |

**Todo lo que no figura en esta matriz está denegado** (R6 y R8). Una funcionalidad nueva no es accesible hasta que se agregue aquí con sus permisos.

### Condiciones de las operaciones implementadas

- **Sesión y CSRF.** `SessionPolicyFilter` evalúa `sesion.iniciar` y `sesion.cerrar` antes de los filtros de login y logout. El login requiere credenciales válidas de una cuenta habilitada y solo puede iniciarlo el Visitante; un usuario autenticado debe cerrar sesión antes de iniciar otra. La reautenticación usa `sesion.reautenticar`, asignada únicamente al Admin, y comprueba su contraseña. `GET /api/auth/csrf` usa la regla especial `sesion.token_csrf`, accesible por cualquier sujeto. Todas las escrituras requieren CSRF válido, incluidos registro, login, logout, reautenticación y aceptación de invitaciones.
- **Cuentas y staff.** Las operaciones autenticadas requieren una cuenta habilitada; el staff necesita una membresía activa con rol asignado. Cambiar el rol o dar de baja a un miembro exige reautenticación vigente y no puede aplicarse a la propia membresía del Admin. Eliminar la propia cuenta realiza una baja lógica e invalida la sesión actual.
- **Ofertas, postulaciones y CV.** Crear una oferta genera un borrador; modificar y publicar solo se permite en estado `BORRADOR`. Una postulación requiere una oferta publicada y disponer de CV; solo se admite una por Talento y oferta. El listado empresarial requiere también `perfil.ver_postulante` para cada postulante. La descarga empresarial entrega la versión del CV vinculada a esa postulación, que puede diferir del CV actual del Talento. Los campos se limitan según R2.
- **Avance.** `PATCH /api/enrollments/{id}/progress` actualiza únicamente `porcentajeAvance` de una inscripción propia, bajo la acción `inscripcion.ver`. No equivale a completar lecciones ni responder entregables.
- **Demo.** Ninguna migración concede `demo.permiso_asignable`: se deniega a los cinco consumidores. El script local [demo-permiso-asignable.sql](../../scripts/demo-permiso-asignable.sql) permite concederla temporalmente a un rol para demostrar el cambio de permisos en datos; se debe retirar ese permiso al terminar.

### Operaciones previstas sin endpoint

Estas operaciones conservan su diseño en el [modelo de autorización](modelo-autorizacion.md), pero no representan rutas disponibles en esta versión ni habilitan rutas implícitas:

| Recurso | Operación prevista | Visitante | Talento | Admin Empresa | Reclutador | Editor |
|---|---|---|---|---|---|---|
| Cuenta | Modificar configuración (distinta del perfil) | — | propia | propia | propia | propia |
| Material | Reemplazar un archivo existente | — | — | org | — | org |
| Lección | Marcar como completada (`leccion.completar`) | — | inscripto | — | — | — |
| Entregable | Enviar respuesta (`entregable.responder`) | — | inscripto | — | — | — |
| Inscripción | Ver inscriptos y su avance (`inscripcion.ver_inscriptos`, campos de R2) | — | — | org | — | org |
| Oferta | Cerrar una publicada | — | — | org | org | — |
| Postulación | Cambiar estado (`postulacion.cambiar_estado`) | — | — | org | org | — |

La etapa implementada de RF6 solo persiste ofertas `BORRADOR`/`PUBLICADA` y postulaciones `ENVIADA`. No hay endpoints de cierre, eliminación o reapertura de ofertas ni de cambio de estado de postulaciones.

## Casos fuera de la matriz

- **Infraestructura de errores y métodos implícitos.** `/error` de Spring Boot solo admite el despacho interno `DispatcherType.ERROR`; las solicitudes directas se deniegan. Si Spring resuelve una solicitud `HEAD` con un handler `GET`, el PEP aplica la misma acción y los mismos alcances. La excepción es el token CSRF, cuya regla especial solo admite `GET`. Login y logout solo habilitan `POST`. Las solicitudes sin un handler con acción declarada se deniegan.
- **Registros de seguridad.** Ningún consumidor accede a ellos desde la aplicación, ni siquiera el Admin de empresa (R19).
- **Alta de empresas y de su Admin.** La realiza el equipo de desarrollo mediante un script versionado en este repositorio, que deja registro de cada alta. No existe pantalla de registro de empresas ni un tipo de usuario Administrador de plataforma.

## Decisiones de diseño

1. **No existe el Administrador de plataforma.** Las tareas de alta de empresas las hace el equipo de desarrollo por fuera de la aplicación, con el script mencionado arriba.
2. **El Talento recibe su conjunto base de permisos al registrarse.** Ese conjunto es su mínimo (R7). No se separa "buscar en el catálogo" de "inscribirse" o "postularse", porque la seguridad no debe impedir el uso central de la plataforma.
3. **Una cuenta es de Talento o de staff, nunca ambas.** Si una persona quiere las dos cosas, necesita dos cuentas.
4. **Cada miembro del staff pertenece a una sola empresa y tiene un solo rol.** No hace falta manejar una "organización activa" en la sesión.
5. **El correo es único por cuenta.** Dos cuentas no pueden compartir correo. Cuando el correo ya existe, la respuesta al registro o a la invitación es genérica, para no revelar que esa persona usa la plataforma.
6. **Las ofertas publicadas solo las ven usuarios autenticados.** El visitante tiene que registrarse como Talento para verlas.
7. **El diseño prevé que el Admin y el Editor vean quién se inscribe a los cursos y proyectos de su empresa, y su avance.** Ese listado todavía no tiene endpoint. El Reclutador no tiene ese permiso. Los campos previstos para el listado se definen en R2.
8. **El staff puede ver el catálogo y las ofertas publicadas de otras empresas.** Restringirlo no aportaría seguridad, porque cualquiera puede crear una cuenta de Talento y verlos.
9. **Si un curso o proyecto se da de baja, los inscriptos conservan el acceso.** No se aceptan inscripciones nuevas, pero quien ya estaba inscripto mantiene el acceso a su material y a su avance.
10. **Las lecciones se numeran, pero la navegación es libre.** Bloquear el acceso por orden agregaría una regla de autorización sin beneficio de seguridad. Para completar el curso se exigen todas las lecciones, en cualquier orden.
11. **Los entregables se validan con respuesta verificable.** La empresa carga las respuestas aceptadas y el backend compara. El detalle está en [restricciones-campo.md](restricciones-campo.md).

## Historial de cambios

- **1.3 (03/11/2026):** revisión RS1 contra los controllers, filtros de sesión, PEP y permisos de las migraciones de `rama-ana`. Se explicita cada método y endpoint con sus cinco consumidores, incluidos autenticación, CSRF, demo, publicación de ofertas, eliminación de cuenta, aceptación de invitaciones y metadatos del CV. Se separan las operaciones previstas sin endpoint.
- **1.2 (27/09/2026):** versión anterior de la matriz por operaciones agrupadas.
