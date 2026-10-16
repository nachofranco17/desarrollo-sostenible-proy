# RS13 — Identificadores que no funcionan como llave de acceso

Requerimiento de seguridad: **RS13** (ASVS / IDOR; documentado como R13 en el modelo)
Versión: 1.0 (15/10/2026)

Un consumidor no debe poder acceder a un recurso por el solo hecho de conocer, adivinar o manipular su identificador. Un UUID difícil de adivinar **no** reemplaza la autorización: es solo una defensa complementaria contra enumeración.

## Relación con RS11

RS13 se implementa **reutilizando** los controles de [RS11 — autorización por instancia](rs11-autorizacion-por-instancia.md):

1. El PEP (`@RequiereAccion`) resuelve el nivel de función.
2. El handler obtiene el sujeto desde la sesión (`Sujetos`), nunca un dueño/empresa del body.
3. La consulta carga la instancia acotada por dueño/empresa/padre + sesión.
4. `autorizar` valida la relación con esa instancia.
5. Recurso ajeno e inexistente producen la misma denegación (403), sin revelar existencia.

No hay un mecanismo de seguridad paralelo ni un “guard de IDs” separado.

## Recursos cubiertos

| Recurso | Cómo se evita que el ID sea llave |
|---|---|
| Perfil / CV propios | Solo rutas `/me`; sin `/profile/{userId}` ni descarga por UUID suelto de CV |
| Inscripción | Lectura/escritura por `{id}` acotada a `talento_id` de sesión + `autorizar` |
| Curso / lección / entregable / material | Consulta con `empresa_id` de sesión y padre de la ruta |
| Oferta (gestión) | `find(id, sujeto.empresaId)` + `autorizar` |
| Oferta / curso publicados | Acceso por alcance `PUBLICADO` (catálogo), no por dueño ajeno |
| Postulación propia | Listado `/applications/me`; creación toma Talento/CV de sesión |
| Perfil/CV de postulante | Ruta anidada oferta + postulación + empresa de sesión + `POSTULANTE` |
| Membresía / staff | `filtrar` por empresa de sesión antes de operar por `memberId` |

## Endpoints que evitan IDs innecesarios

El recurso se deduce de la sesión; el cliente no elige el dueño:

| Endpoint | Identidad efectiva |
|---|---|
| `GET/PATCH /api/profile/me` | Usuario de la sesión |
| `GET/POST /api/profile/me/curriculum` (+ `/metadata`) | CV actual del Talento de la sesión |
| `GET /api/enrollments/me` | Inscripciones del Talento de la sesión |
| `GET /api/applications/me` | Postulaciones del Talento de la sesión |

Intentar `GET /api/profile/{userId}` no es una ruta de negocio: se deniega (misma familia de respuesta que un recurso no autorizado).

## Endpoints que reciben IDs y cómo los validan

| Endpoint | Validación |
|---|---|
| `GET/PATCH /api/enrollments/{id}` (+ `/progress`) | `findOwned(id, talentoSesión)` + `autorizar(inscripcion.ver, …)` |
| `POST /api/enrollments` | Solo `cursoId` en body; `talento_id` = sesión; curso publicado + `autorizar` |
| `/api/company/courses/{id}/**` | Curso por `id` + `empresaId` de sesión; hijos con `curso_id` + `empresa_id` |
| `/api/company/offers/{id}/**` | Oferta por `id` + `empresaId` de sesión |
| `/api/company/offers/{id}/applications/{applicationId}/**` | Triple: oferta + postulación + empresa de sesión; luego `POSTULANTE` / listado |
| `/api/offers/{id}` (publicado) | Solo si está publicada + `autorizar(oferta.ver, …)` |
| `/api/catalog/{id}` | Solo publicado + `autorizar(catalogo.ver, …)` |
| `/api/staff/.../{memberId}/...` | `filtrar` por org + `autorizar` sobre la membresía |

Manipular URL, query, body, Postman o DevTools no cambia el sujeto ni el alcance de la consulta.

## UUID como defensa complementaria

Todas las entidades de negocio usan UUID (ver modelo de autorización, R13). Eso dificulta la enumeración secuencial, pero:

- conocer el UUID exacto de un recurso ajeno **sigue denegado**;
- la defensa principal es la autorización por instancia (RS11), no la impredecibilidad del id.

No se migró ningún esquema de IDs: ya eran UUID.

## Listados y relaciones

- `/enrollments/me` y `/applications/me` solo listan recursos del sujeto.
- El listado empresarial de postulantes exige la oferta de la empresa de sesión; no mezcla ofertas ajenas.
- DTOs de perfil propio no exponen IDs de otros usuarios; el progreso en el perfil se deriva de las inscripciones propias.
- No hay endpoint de descarga empresarial por UUID suelto de CV.

## Pruebas

`Rs13IdentifierNotAccessKeyTest`:

1. Talento A accede a su inscripción por ID → 200.
2. Talento A usa el ID real de la inscripción de B → 403; el avance de B no cambia.
3. A sustituye su UUID por el UUID válido de B → 403.
4. ID inexistente → 403 con el mismo cuerpo que el recurso ajeno.
5. Conocer exactamente el ID de B no permite leer ni listarlo en `/me`.
6. `/profile/me` y `/enrollments/me` funcionan sin ID de cliente; `/profile/{id}` se deniega.
7. `talentoId`/`usuarioId`/`userId` en body de inscripción o perfil no cambian el propietario efectivo.
8. Entregable de proyecto A bajo ruta de proyecto B, y postulación de oferta A bajo oferta B → 403 indistinguible de UUID inventado.

Cobertura relacionada ya existente: `Rs11InstanceAuthorizationTest`, `EnrollmentIntegrationTest`, `ApplicationIntegrationTest`, `CourseIntegrationTest`, `ProfileIntegrationTest`.

```bash
cd backend
./mvnw "-Dtest=Rs13IdentifierNotAccessKeyTest" test
```

## Cómo probar manualmente

1. Registrar Talento A y Talento B. Publicar un curso e inscribir a ambos.
2. Con A autenticado, obtener el `id` de la inscripción de B (p. ej. desde la base o una sesión previa de B).
3. `GET /api/enrollments/{id-de-B}` y `PATCH /api/enrollments/{id-de-B}/progress` → 403.
4. `GET /api/enrollments/{id-de-A}` → 200; `GET /api/enrollments/me` → incluye solo las de A.
5. `GET /api/profile/me` → 200 sin enviar userId; `PATCH` con `userId`/`usuarioId` de B → 400 y el perfil de B no cambia.
6. (Opcional) En la misma empresa, tomar un entregable de proyecto 1 y llamarlo bajo `/company/courses/{proyecto2}/deliverables/{entregable1}` → 403.

## Fuera de alcance / pendiente de funcionalidad

Los endpoints de negocio de `leccion.completar` y `entregable.responder` aún no existen. Cuando se implementen, deberán aplicar el mismo patrón (sesión + consulta acotada + `autorizar`); no se agregaron aquí.
