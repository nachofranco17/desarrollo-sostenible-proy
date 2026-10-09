# Modelo de autorización

Requerimiento de seguridad: **R4** (ASVS v5.0.0-8.2.1, 8.2.2)
Versión: 1.5 (02/10/2026)

Este documento define cómo se representan los permisos, cuál es el contrato del evaluador de política y qué datos necesita el evaluador para decidir. Traduce a un diseño implementable la [matriz de control de acceso](matriz-control-acceso.md) (R1) y las [restricciones de campo](restricciones-campo.md) (R2).

## 1. Enfoque

- **Los permisos son datos, no código.** El código de una funcionalidad nunca pregunta por el rol del usuario ("¿es Reclutador?"). Pregunta si el usuario puede ejecutar una **acción** sobre un **recurso**, y la respuesta la calcula el evaluador de política.
- **El rol es un agrupador de permisos guardado en la base.** Cada permiso combina un rol, una acción y un **alcance**, que es la condición sobre la relación entre el usuario y el objeto.
- **Cambiar quién puede hacer qué es editar datos**, no tocar el código de las funcionalidades.
- **La decisión se toma en dos niveles:**
  - **Nivel de función (8.2.1):** ¿el sujeto puede invocar esta acción? Lo resuelve el PEP antes de entrar al handler.
  - **Nivel de dato (8.2.2):** ¿puede hacerlo sobre esta instancia en particular? Lo resuelve el evaluador en la consulta.

## 2. Alcances

Cada alcance es un predicado que se implementa una sola vez, dentro del evaluador.

| Alcance | Se cumple cuando |
|---|---|
| `GLOBAL` | La operación no recae sobre una instancia existente. Solo se usa para registrarse. |
| `PUBLICADO` | El curso/proyecto está en estado publicado, o la oferta en estado publicada. |
| `PROPIO` | El recurso pertenece al usuario de la sesión. |
| `ORG` | El `empresa_id` del recurso coincide con el de la sesión. |
| `POSTULANTE` | El Talento dueño del recurso tiene una postulación a una oferta de la empresa de la sesión. |
| `INSCRIPTO` | El sujeto tiene una inscripción al curso/proyecto al que pertenece el recurso (material, lección o entregable), aunque haya sido dado de baja. |

## 3. Catálogo de acciones y permisos

Cada endpoint declara una única acción. Esta tabla es la traducción de la matriz de control de acceso a códigos, y es el contenido de la tabla `permiso` que carga Flyway. Si cambia la matriz, cambia esta tabla, y viceversa.

| Acción | Operación | Visitante | Talento | Admin | Reclutador | Editor |
|---|---|---|---|---|---|---|
| `cuenta.registrar` | Registrarse como Talento | GLOBAL | — | — | — | — |
| `sesion.iniciar` | Iniciar sesión | GLOBAL | — | — | — | — |
| `sesion.cerrar` | Cerrar la propia sesión | — | PROPIO | PROPIO | PROPIO | PROPIO |
| `sesion.reautenticar` | Confirmar la identidad antes de una operación sensible (R23) | — | — | PROPIO | — | — |
| `cuenta.gestionar` | Ver, modificar configuración y eliminar la propia cuenta | — | PROPIO | PROPIO | PROPIO | PROPIO |
| `perfil.gestionar` | Ver y modificar el propio perfil | — | PROPIO | — | — | — |
| `perfil.ver_postulante` | Ver el perfil de un postulante | — | — | POSTULANTE | POSTULANTE | — |
| `catalogo.ver` | Buscar y ver cursos/proyectos publicados | PUBLICADO | PUBLICADO | PUBLICADO | PUBLICADO | PUBLICADO |
| `curso.gestionar` | Crear, modificar, cambiar estado y eliminar cursos/proyectos, lecciones y entregables | — | — | ORG | — | ORG |
| `curso.ver_borrador` | Ver borradores | — | — | ORG | — | ORG |
| `material.subir` | Subir o reemplazar material | — | — | ORG | — | ORG |
| `material.descargar` | Descargar material | — | INSCRIPTO | ORG | — | ORG |
| `inscripcion.crear` | Inscribirse a un curso/proyecto | — | PUBLICADO | — | — | — |
| `inscripcion.ver` | Ver su inscripción y avance | — | PROPIO | — | — | — |
| `leccion.completar` | Marcar una lección como completada | — | INSCRIPTO | — | — | — |
| `entregable.responder` | Enviar respuesta a un entregable | — | INSCRIPTO | — | — | — |
| `inscripcion.ver_inscriptos` | Ver inscriptos y su avance | — | — | ORG | — | ORG |
| `oferta.ver` | Ver ofertas publicadas | — | PUBLICADO | PUBLICADO | PUBLICADO | PUBLICADO |
| `oferta.gestionar` | Crear, modificar, cerrar y ver borradores | — | — | ORG | ORG | — |
| `postulacion.crear` | Postularse a una oferta | — | PUBLICADO | — | — | — |
| `postulacion.ver` | Ver sus postulaciones y su estado | — | PROPIO | — | — | — |
| `postulacion.ver_listado` | Ver el listado de postulantes | — | — | ORG | ORG | — |
| `postulacion.cambiar_estado` | Cambiar el estado de una postulación | — | — | ORG | ORG | — |
| `curriculum.subir` | Subir o reemplazar el currículum | — | PROPIO | — | — | — |
| `curriculum.descargar` | Descargar un currículum | — | PROPIO | POSTULANTE | POSTULANTE | — |
| `staff.invitar` | Invitar un usuario al staff | — | — | ORG | — | — |
| `staff.cambiar_rol` | Cambiar el rol de un miembro | — | — | ORG | — | — |
| `staff.dar_baja` | Dar de baja a un miembro | — | — | ORG | — | — |
| `staff.ver` | Ver miembros y su rol | — | — | ORG | — | — |

**Caso especial: `invitacion.aceptar`.** No tiene filas en la tabla porque el sujeto no tiene sesión ni rol. El PEP la trata como una acción propia cuya única condición es que el token de invitación sea válido y no haya expirado.

### Funcionalidades nuevas (R8)

Toda funcionalidad nueva queda inaccesible hasta que se definan sus permisos de forma explícita. Para habilitarla:

1. Agregar la acción a la tabla de esta sección, con el alcance de cada rol. Una acción que solo existe en el código no se puede usar.
2. Cargar sus permisos con una migración nueva de Flyway. Una migración ya aplicada no se edita.
3. Anotar cada endpoint con `@RequiereAccion("<acción>")`. Un endpoint sin la anotación se deniega siempre.
4. Resolver el nivel de dato en el handler con `autorizar` o `filtrar` (sección 6).

Las pruebas automáticas verifican esta convención. `EndpointConventionTest` falla si algún endpoint no tiene la anotación o declara una acción que no está en esta tabla, y `PolicyContractTest` falla si los permisos cargados en la base no coinciden con ella. La única excepción es el controller de errores de Spring Boot (`/error`), que solo atiende el despacho interno de error (sección 6).

## 4. Condiciones adicionales

Son reglas que no se expresan como alcance. Viven en el evaluador, asociadas a la acción, y nunca en el código de la funcionalidad.

| Acción | Condición |
|---|---|
| Cualquier acción autenticada | La cuenta del sujeto está activa. |
| Cualquier acción de staff | La membresía del sujeto está activa y tiene un rol asignado. |
| `staff.cambiar_rol`, `staff.dar_baja` | La membresía objetivo no es la del sujeto, y el sujeto se reautenticó (R23). |
| `postulacion.cambiar_estado` | La transición está entre las permitidas (ver restricciones de campo). |
| `perfil.ver_postulante` | El teléfono solo se incluye si la postulación está en Preseleccionada o Seleccionada. |

El límite de 20 respuestas por minuto de `entregable.responder` no es una regla de autorización. Se aplica en el endpoint, con Bucket4j.

## 5. El sujeto

```java
public record Sujeto(
    UUID usuarioId,      // null para el visitante
    TipoCuenta tipoCuenta, // VISITANTE, TALENTO o STAFF
    UUID empresaId,      // solo staff
    Rol rol              // VISITANTE, TALENTO, ADMIN, RECLUTADOR o EDITOR; null si el staff aún no tiene rol
) {}
```

- El sujeto se arma **desde la base en cada solicitud**, a partir del `usuarioId` guardado en la sesión del servidor. Nunca se toma de un token autocontenido ni de la solicitud (R16).
- Para no consultar la base en cada solicitud se puede usar un caché, siempre con **invalidación explícita** cuando cambia el rol, el estado de la membresía o el de la cuenta.
- El visitante es un sujeto anónimo con rol `VISITANTE`.

## 6. Contrato del evaluador

```java
public interface EvaluadorPolitica {

    /** Nivel de función (8.2.1): ¿el rol del sujeto tiene algún permiso para la acción? Lo usa el PEP. */
    boolean puedeInvocar(Sujeto sujeto, String accion);

    /** Nivel de dato (8.2.2): ¿puede ejecutar la acción sobre esta instancia? */
    boolean autorizar(Sujeto sujeto, String accion, Object recurso);

    /** Condición que se agrega a toda consulta sobre el tipo de recurso (R11, R15). */
    <T> Specification<T> filtrar(Sujeto sujeto, String accion, Class<T> tipo);

    /** Campos que el sujeto puede leer o escribir (R2, R12). */
    Set<String> camposLegibles(Sujeto sujeto, String accion, Object recurso);
    Set<String> camposEscribibles(Sujeto sujeto, String accion, Object recurso);
}
```

Reglas del contrato:

- **Denegar por defecto.** Si no hay un permiso que aplique, `puedeInvocar` y `autorizar` devuelven `false` y `filtrar` devuelve una condición que no coincide con ninguna fila (`cb.disjunction()`). Nunca una condición vacía, que coincidiría con todas (R6).
- **Error equivale a denegar.** Cualquier excepción durante la evaluación se trata como denegación (R6), tanto en el evaluador como en el PEP y en el filtro de login y logout. El cliente recibe la misma respuesta genérica que ante cualquier denegación. La causa queda solo en el log del servidor, sin datos de la solicitud como contraseñas, tokens o parámetros.
- **Una falla del registro no impide denegar.** Si la interfaz de registro (R18) falla, la denegación ocurre igual y con la misma respuesta. El error queda en el log del servidor.
- **Escrituras transaccionales.** Toda operación de escritura se ejecuta dentro de una transacción (`@Transactional`), para que una denegación o un error a mitad de la operación no deje cambios aplicados.

### Declaración de la acción en cada endpoint

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiereAccion {
    String value();
}

@PostMapping("/lecciones/{id}/completar")
@RequiereAccion("leccion.completar")
public void completar(@PathVariable UUID id) { ... }
```

El PEP (R5) es un `AuthorizationManager` de Spring Security aplicado a todas las rutas de `/api`. Antes de que se ejecute el handler, busca cuál va a atender la solicitud y lee su anotación:

- Si no hay handler o no es un método de controller, rechaza la solicitud.
- Si el método no tiene la anotación, rechaza (R8).
- Si la acción es `invitacion.aceptar`, solo deja pasar con un token de invitación válido.
- Si `puedeInvocar` devuelve `false`, rechaza.
- Si ocurre cualquier error durante la evaluación, rechaza (R6).
- En cualquier otro caso, deja pasar la solicitud al handler, que resuelve el nivel de dato.

Los handlers deniegan lanzando `AccessDeniedException`. Toda denegación, sea del PEP, de un filtro o de un handler, termina en un único componente que arma la respuesta: 401 si no hay sesión y 403 si la hay, siempre con el mismo mensaje genérico. Ese componente además la informa a `RegistroAccesos` (RS18). La implementación `RegistroAccesosEstructurado` emite un evento JSON parseable con metadatos seguros (usuario, origen, recurso, operación, motivo); el detalle está en [rs18-registro-accesos-denegados.md](rs18-registro-accesos-denegados.md). La interfaz permanece:

```java
public interface RegistroAccesos {
    void denegado(Sujeto sujeto, String accion, String recurso);
}
```

Casos que no pasan por un controller propio y se contemplan explícitamente:

- **Login y logout.** Los procesan los filtros de Spring Security, antes de llegar a cualquier controller, por lo que la anotación no los cubre. Un filtro propio que se ejecuta antes que ellos los asocia a `sesion.iniciar` y `sesion.cerrar` y consulta al mismo evaluador que el PEP.
- **Token CSRF (`GET /api/auth/csrf`).** Lo entrega ese mismo filtro sin pasar por el PEP. Es infraestructura de Spring Security y solo devuelve el token antifalsificación de la sesión.
- **Endpoint `/error` de Spring Boot.** Lo atiende un controller de Spring Boot sin la anotación. Solo se permite el despacho interno de error (`DispatcherType.ERROR`), que escribe la respuesta de un error ya ocurrido sin deshacer la denegación. Si el cliente pide `/error` directamente, se rechaza. La respuesta no incluye mensajes ni trazas (`server.error.*`).
- **Rutas fuera de `/api`.** Se rechazan con `denyAll()` antes de llegar al PEP, aunque el handler tenga la anotación.

### Uso en el handler

Toda lectura pasa por `filtrar`, **incluidas las búsquedas por id**:

```java
Curso curso = cursos.findOne(
        where(idIgual(id)).and(evaluador.filtrar(sujeto, "curso.gestionar", Curso.class)))
    .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
```

Así, "no existe" y "no tiene permiso" producen la misma respuesta de prohibido, sin revelar la existencia del recurso (R11).

## 7. Modelo de datos

Todos los identificadores son UUID (R13). Las entidades vinculadas a una empresa llevan `empresa_id`, aunque se pueda deducir por otra relación. El servidor lo copia al crear el registro y nunca lo toma de la solicitud (R15).

| Entidad | Atributos que usa la autorización |
|---|---|
| Usuario | id, correo (único), hash de contraseña, tipo_cuenta, estado |
| PerfilTalento | usuario_id, nombre, apellido, correo, teléfono, especializaciones |
| Empresa | id, nombre |
| Membresía | usuario_id (único), empresa_id, rol (nulo hasta asignarse), estado, hash del token de invitación, expiración del token |
| Permiso | rol, acción, alcance |
| CursoProyecto | id, empresa_id, tipo (curso / proyecto), estado |
| Lección | id, curso_id, empresa_id, número |
| Entregable | id, proyecto_id, empresa_id, número, enunciado, pista |
| RespuestaAceptada | id, entregable_id, hash |
| Material | id, curso_id, empresa_id, archivo_id |
| Inscripción | id, talento_id, curso_id, fecha |
| LecciónCompletada | inscripción_id, lección_id, fecha |
| EntregableAprobado | inscripción_id, entregable_id, fecha |
| Oferta | id, empresa_id, estado |
| Postulación | id, talento_id, oferta_id, empresa_id, currículum_id, estado, fecha |
| Archivo | id, dueño_id, tipo, ubicación privada en el almacenamiento |
| RegistroSeguridad | Almacén de solo agregado con credenciales propias (R18, R19) |

### Estados

| Entidad | Estados |
|---|---|
| Usuario | activo, dado de baja |
| Membresía | pendiente, activa, baja |
| CursoProyecto | borrador, publicado, bajado |
| Oferta | borrador, publicada, cerrada |
| Postulación | enviada, preseleccionada, seleccionada, descartada |

## 8. Stack

| Capa | Tecnología | Requerimientos que apoya |
|---|---|---|
| Framework | Spring Boot + Spring Security | R5, R6, R8, R10 |
| Acceso a datos | Spring Data JPA con Specifications | R11, R15 |
| Base de datos | H2 (archivo local; SQL en modo compatible con PostgreSQL) | Persistencia de sesiones, permisos y dominio. R19 (protección del almacén de logs) sigue pendiente |
| Sesiones | Spring Session JDBC (en el servidor, no JWT) | R16 |
| Migraciones | Flyway (esquema y carga de la tabla de permisos) | R1, R4 |
| Archivos | Disco local (`STORAGE_DIR`). Toda descarga pasa por un endpoint del backend (`material.descargar`; `curriculum.descargar` cuando exista). El modelo prevé MinIO más adelante | R5, R14 |
| Límite de envíos | Bucket4j | Entregables |
| Pruebas | JUnit 5 + MockMvc + servidor HTTP real sobre H2 | R20 |
| Frontend | React + TypeScript, servido por su propio servidor (Vite en desarrollo). El backend expone solo la API | — |

Configuración obligatoria del framework. R22 quedó fuera del alcance como requerimiento independiente, pero estas reglas se mantienen y se verifican con las pruebas de R20:

- Spring Security no deniega todo por defecto. La configuración debe cerrar con `anyRequest().denyAll()`.
- La protección CSRF no se desactiva, porque la sesión viaja en una cookie.
- Las rutas de `/api` se delegan al PEP (`access(pep)`). No se agregan reglas por rol en Spring Security, para que el PEP sea el único punto que conoce las acciones (R5).
- `/error` solo se permite para el despacho interno de error, nunca para pedidos directos.
- La caché de solicitudes de Spring Security (`requestCache`) está desactivada, para que una solicitud denegada no cree una sesión en el servidor.

En desarrollo, el servidor de Vite reenvía las llamadas a `/api` hacia el backend (`server.proxy`). Así el navegador ve un único origen, la cookie de sesión funciona sin configurar CORS y `SameSite` puede quedar en `Strict` o `Lax`.

## 9. Decisiones de diseño

1. **Roles como agrupadores de permisos en datos.** Como cada miembro del staff tiene un único rol, asignar permisos usuario por usuario no aporta nada. Lo que distingue este modelo de un RBAC con chequeos de rol en el código es dónde se decide: un evaluador, sobre permisos y relaciones con el objeto.
2. **Toda consulta pasa por `filtrar`, incluso por id.** Unifica la verificación por instancia y evita revelar la existencia de recursos (R11).
3. **Sesión en el servidor en lugar de JWT.** Permite que los cambios de permisos tengan efecto inmediato (R16).
4. **Identificadores UUID en todas las entidades.** Impiden enumerar recursos (R13).
5. **`empresa_id` en toda entidad vinculada a una empresa.** Permite que el filtro por organización sea una condición directa, sin joins (R15).
6. **Los archivos se sirven siempre a través del backend.** Se descartaron los enlaces firmados porque la descarga no atravesaría el PEP (R5). El almacenamiento no es accesible desde el navegador.
