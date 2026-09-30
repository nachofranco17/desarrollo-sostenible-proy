# RF2 — Perfil del Talento

Implementamos el perfil del usuario con rol Talento sobre la arquitectura que ya tenía el proyecto (sesión en cookie, permisos en base, JDBC y React).

## Qué quedó hecho

Desde `/mi-cuenta`, el Talento puede entrar a `/perfil`. Ahí ve:

- datos personales y de contacto;
- especializaciones;
- preferencias de notificaciones (novedades de cursos y ofertas laborales);
- configuración de cuenta (cambio de contraseña);
- progreso de cursos, proyectos completados y logros (solo lectura).

Puede editar y guardar lo editable. Al salir y volver, los cambios siguen ahí porque se persisten en base.

Progreso, proyectos y logros se muestran vacíos por ahora. No metimos catálogo, inscripciones ni cursos: cuando existan esos datos, el mismo endpoint los va a poder devolver sin cambiar el contrato de RF2.

## API

| Método y ruta | Quién | Resultado |
|---|---|---|
| `GET /api/profile/me` | Talento con sesión y permiso `perfil.gestionar` | 200 con el perfil propio |
| `PATCH /api/profile/me` | Igual | 200 con el perfil actualizado |

La identidad sale siempre de la sesión (`Principal`). No hay rutas del tipo `/api/profile/{userId}`.

Campos que acepta el `PATCH` (lista cerrada; cualquier otro campo → 400 y no se aplica nada):

- `nombre`, `apellido`, `correo`, `telefono`
- `especializaciones` (lista de strings del catálogo)
- `notificarNovedadesCursos`, `notificarOfertas`
- `passwordActual`, `passwordNueva` (opcionales; si se cambia la clave hay que mandar las dos)

El `GET` incluye `especializacionesDisponibles` con el catálogo permitido.

No se pueden escribir rol, progreso, logros, ids ni atributos internos.

## Base de datos

Migración `V3__perfil_talento.sql`:

- tabla `perfil_talento` (teléfono, especializaciones, preferencias de notificación);
- permiso `perfil.gestionar` y `cuenta.gestionar` para el rol Talento;
- backfill de perfiles para Talentos ya existentes.

Nombre, apellido y correo siguen en `usuario` (como en el registro). El update de perfil los actualiza ahí y el resto en `perfil_talento`.

## Seguridad

- Solo un Talento activo con `perfil.gestionar` entra a estos endpoints.
- Staff autenticado recibe 403 (no se le invalida la sesión solo por intentar).
- Talento A no puede apuntar al perfil de B: no hay id en la URL y cualquier id en el body se rechaza como campo desconocido.
- La contraseña se valida con el `PasswordEncoder` existente (PBKDF2); nunca se devuelve.

## Frontend

Ruta `/perfil` (solo rol Talento), acceso desde “Ver mi perfil” en `/mi-cuenta`. Misma estética y mismo manejo de errores/CSRF que el resto.

## Pruebas

`ProfileIntegrationTest` cubre lectura propia, edición y persistencia, cambio de contraseña, rechazo de campos no autorizados, intento horizontal, rutas con id, CSRF/auth y denegación a staff.

## Cómo probar manualmente

1. Registrar e iniciar sesión como Talento.
2. Ir a **Ver mi perfil**.
3. Editar nombre, contacto, especializaciones y notificaciones; guardar.
4. Volver a mi cuenta y entrar otra vez al perfil: deben verse los valores nuevos.
5. (Opcional) Cambiar la contraseña desde el mismo formulario y volver a ingresar con la nueva.
6. Con otro Talento, intentar `PATCH /api/profile/me` mandando el id del primero en el body: el backend responde 400 y no cambia nada del otro.

## Validación de entrada (actualización)

El backend es el control efectivo (`ProfileUpdateParser`). El frontend solo mejora UX; una request manual no puede saltear las reglas.

### Allowlist

Campos editables: `nombre`, `apellido`, `correo`, `telefono`, `especializaciones`, `notificarNovedadesCursos`, `notificarOfertas`, `passwordActual`, `passwordNueva`.

Cualquier otro campo (`id`, `userId`, `role`, `permissions`, `isAdmin`, `progress`, `achievements`, etc.) → **400** y no se aplica ningún cambio.

### PATCH parcial

- Campo **no enviado**: no se modifica.
- `telefono: null` o `""` (tras trim): se limpia (ausencia permitida).
- `especializaciones: null`: rechazado (hay que enviar lista).
- Preferencias: solo booleanos JSON reales (`true`/`false`); no `"true"`, `"yes"` ni objetos/arrays.

### Tipos y límites

| Campo | Tipo | Reglas |
|---|---|---|
| `nombre` / `apellido` | string | trim; 2–80; sin caracteres de control; letras Unicode, espacios, tildes, apóstrofes y guiones |
| `correo` | string | trim + minúsculas; máx. 254; formato email; unicidad en `usuario` |
| `telefono` | string o null | 7–30; solo `0-9`, `+`, espacios, guiones y paréntesis; ≥7 dígitos |
| `especializaciones` | array de string | solo valores del catálogo (`SpecializationCatalog`); sin duplicados |
| notificaciones | boolean | estrictamente boolean |

**Normalizar ≠ validar:** se permite `trim` (y normalizar correo). Una entrada inválida se **rechaza**, no se “arregla”. Ejemplo: `099abc123` es inválido y se rechaza; **no** se convierte en `099123`.

### Especializaciones

Catálogo fijo expuesto en `GET /api/profile/me` como `especializacionesDisponibles`. El frontend solo permite elegir de ese listado.

### Errores

Validación → HTTP 400 con `{ "message": "..." }` (mismo formato del proyecto). Sin stack traces ni SQL.

### Protecciones adicionales (seguridad de request)

- `PATCH /api/profile/me` exige `Content-Type: application/json` (`consumes`).
- Tamaño máximo del body HTTP: **16KB** (`server.tomcat.max-http-post-size`), alineado al límite ya usado para formularios.
- Si falla la unicidad de correo por condición de carrera, se responde el mismo mensaje genérico (`No se pudo guardar el perfil con esos datos.`) sin exponer detalle interno; la transacción no deja cambios parciales.
