# RF4 — Inscripción y seguimiento del avance

El Talento autenticado puede inscribirse a un curso o proyecto publicado, ver sus inscripciones y registrar el porcentaje de avance. Reutiliza `curso` (RF5), el catálogo (RF3) y los permisos `inscripcion.crear` / `inscripcion.ver` ya cargados en la matriz.

## Modelo

Tabla `inscripcion` (migración `V8__inscripciones.sql`):

| Campo | Significado |
|---|---|
| `id` | UUID de la inscripción |
| `talento_id` | Usuario Talento de la sesión (nunca tomado del body) |
| `curso_id` | Curso o proyecto (`curso.id`) |
| `fecha` | Momento de la inscripción |
| `porcentaje_avance` | Entero persistido, `CHECK (0..100)`, default `0` |

Restricción `UNIQUE (talento_id, curso_id)`: un Talento no puede tener dos inscripciones al mismo elemento, también bajo concurrencia.

No se agregaron estados de inscripción: el diseño actual no los contempla.

### Vínculo con el Talento

`talento_id` se toma del sujeto autenticado (`Sujetos.current()` / cuenta `TALENTO` activa). El body de creación solo admite `cursoId`.

### Vínculo con curso/proyecto

`curso_id` apunta a `curso`. Solo se admite inscripción si el curso/proyecto está **PUBLICADO** (`inscripcion.crear` con alcance PUBLICADO). Dados de baja no admiten inscripciones nuevas.

## Progreso

El avance se guarda en `porcentaje_avance` (entero `0..100`) y es de **solo lectura** para el Talento. Según `restricciones-campo.md`, lo calcula el backend a partir de las lecciones completadas y los entregables aprobados; el cliente nunca lo envía, para que un Talento no pueda asignarse un avance que no tiene.

Hasta el Sprint 2 existía `PATCH /api/enrollments/{id}/progress`, con el que el Talento escribía el porcentaje que quisiera. Se eliminó por la incidencia DS-33 (RS12). Como todavía no existen las APIs para marcar lecciones o responder entregables, el avance queda en `0` hasta que se implemente ese cálculo.

## Endpoints

Base: `/api/enrollments`.

| Método y ruta | Acción | Resultado |
|---|---|---|
| `POST /api/enrollments` | `inscripcion.crear` | 201: inscripción creada; body allowlist `{ "cursoId" }` |
| `GET /api/enrollments/me` | `inscripcion.ver` | 200: `{ items }` del Talento de la sesión |
| `GET /api/enrollments/{id}` | `inscripcion.ver` | 200: inscripción propia; ajena/inexistente → 403 |

### Respuesta de ítem

`id`, `cursoId`, `tipo` (`CURSO`/`PROYECTO`), `titulo`, `porcentajeAvance`, `fecha`.

### Errores relevantes

| Situación | Código |
|---|---|
| Ya inscripto (o violación del UNIQUE) | 409 |
| Porcentaje inválido / campos no permitidos / `cursoId` inválido | 400 |
| Sin autenticación o sin permiso de función | 401 / 403 |
| Curso no publicado / inscripción ajena | 403 |

## Autorización por instancia (RS11)

- Creación: curso publicado + `autorizar(..., inscripcion.crear, course)`.
- Lectura/actualización: consulta acotada a `talento_id` de la sesión + `autorizar(..., inscripcion.ver, enrollment)`.
- El evaluador reconoce `Enrollment` como recurso `PROPIO` (`talentoId`).
- Talento A no puede leer ni actualizar la inscripción de B aunque conozca el UUID.

## Validaciones

- Usuario autenticado con rol/permiso correspondiente (PEP + servicio).
- Curso/proyecto existente y publicado.
- Inscripción perteneciente al Talento de la sesión.
- Ausencia de duplicados (chequeo + UNIQUE).
- Identificadores UUID válidos.
- Allowlist de campos en la creación.

## Frontend

- Detalle del catálogo (`/catalogo/:id`): botón de inscripción para Talento; si ya está inscripto, se indica y se oculta la acción.
- Vista `/inscripciones`: listado propio con nombre, tipo y avance (solo lectura).
- Enlace desde Mi cuenta.

## Pruebas

`EnrollmentIntegrationTest` cubre inscripción a curso y proyecto, asociación al Talento, listado, que el Talento no pueda escribir su avance por ninguna ruta ni método (DS-33), duplicados de curso y proyecto, UNIQUE bajo insert concurrente simulado, aislamiento entre Talentos y requests sin autenticación.

```bash
cd backend
./mvnw '-Dtest=EnrollmentIntegrationTest' test
```

## Cómo probar manualmente

1. Backend `dev` + frontend. Registrar/iniciar sesión como Talento.
2. Abrir http://127.0.0.1:5173/catalogo (si no hay publicados, publicar uno como Admin/Editor en `/gestion`).
3. Abrir el detalle de un curso o proyecto → **Inscribirme**.
4. Ir a **Mis inscripciones** (`/inscripciones`) y verificar el ítem con avance 0%.
5. Comprobar que la tabla no ofrece ningún control para modificar el avance.
6. Volver al detalle e intentar inscribirse otra vez: no debe ofrecer la acción (o el backend responde 409 si se fuerza la request).
7. Llamar `PATCH /api/enrollments/{id}/progress` con `{ "porcentajeAvance": 100 }`, tanto sobre la propia inscripción como sobre la de otro Talento → 403; el avance no cambia.
