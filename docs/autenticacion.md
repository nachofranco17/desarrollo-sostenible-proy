# Registro e inicio de sesión

## Alcance

Se toma el informe `InformeProyecto.pdf` como contexto del proyecto XPerience. Para el alta de cuentas prevalece el requisito actualizado: **registro público de Talentos**, alta de empresas/Administradores por script y resto del staff por invitación RF7. No se implementa el registro público de empresas mencionado en la versión anterior del informe.

El envío y la aceptación de invitaciones de RF7 siguen pendientes. Las pruebas crean membresías que representan una invitación aceptada. El staff requiere cuenta activa, membresía activa y rol explícitamente asignado para iniciar sesión y acceder a su cuenta. Esto reemplaza la excepción anterior para staff sin rol y sigue el modelo de `docs/seguridad`. Ver [permisos mínimos](permisos-minimos.md).

## Contrato HTTP

Todas las respuestas de error usan `{ "message": "..." }`. La sesión se conserva mediante cookie; el cliente debe enviarla en solicitudes posteriores.

| Método y ruta | Entrada | Resultado |
|---|---|---|
| `GET /api/auth/csrf` | Sin autenticación | 200: `token` y `headerName`; crea una sesión anónima si hace falta |
| `POST /api/auth/register` | JSON: `correo`, `nombre`, `apellido`, `password` | 202 con mensaje genérico tanto si crea como si ya existe |
| `POST /api/auth/login` | Form URL encoded: `correo`, `password` | 204 y sesión autenticada; 401 genérico si falla |
| `GET /api/account` | Sesión activa y permiso `cuenta.gestionar` | 200: datos propios, tipo, rol y empresa; 401 sin sesión; 403 al revocar acceso |
| `POST /api/auth/logout` | Cookie de sesión y CSRF | 204; elimina la sesión y la cookie |
| `POST /api/auth/reauthenticate` | JSON con `password`, sesión de Administrador y CSRF | 204 si la contraseña actual coincide; 401 si es incorrecta |
| `PATCH /api/staff/{usuarioId}/rol` | JSON con `rol`: `ADMIN`, `RECLUTADOR` o `EDITOR`, sesión, CSRF y reautenticación vigente | 204; solo Administrador de la misma empresa, sobre otro miembro activo |

Antes de cada POST o PATCH, obtener el token de `/api/auth/csrf` y enviarlo en el header cuyo nombre devuelve esa respuesta (`X-CSRF-TOKEN`). Repetir después del login/logout, ya que cambia el token. Sin CSRF válido el servidor devuelve 403. El frontend ya realiza este flujo para las operaciones que ofrece.

Ejemplo de cuerpo de registro:

```json
{
  "correo": "talento@example.test",
  "nombre": "Ana",
  "apellido": "Prueba",
  "password": "una frase larga elegida por vos"
}
```

Respuesta 202 en ambos casos (nuevo y existente):

```json
{
  "message": "Solicitud recibida. Si los datos permiten crear una cuenta, podrás iniciar sesión con las credenciales indicadas."
}
```

El correo se normaliza con minúsculas y recorte de espacios antes de validarlo. La base aplica unicidad; ante una carrera entre registros, el intento perdedor recibe el mismo 202. Un duplicado nunca actualiza una cuenta ni inicia sesión. Los campos desconocidos se rechazan con 400, sin aplicar cambios.

## Persistencia y controles

- `usuario`: UUID, correo único, nombre/apellido, hash PBKDF2, tipo y estado.
- `empresa` y `membresia`: cada usuario STAFF pertenece a una empresa; el rol es nulo por defecto hasta asignarlo explícitamente. La membresía debe estar activa y tener rol para iniciar sesión.
- `permiso`: los 54 permisos de la tabla de `docs/seguridad/modelo-autorizacion.md`. La migración V4 elimina `AUTENTICADO`/`cuenta.ver` y carga las acciones y alcances documentados. La sesión contiene la identidad y la autorización consulta la base en cada acceso.
- `alta_empresa`: registro de altas hechas mediante el script, con empresa, Administrador, ejecutor y fecha; no guarda contraseñas. No sustituye a la auditoría completa de R18/R19.
- `SPRING_SESSION*`: sesiones JDBC. El identificador cambia al autenticar; logout elimina la sesión y una cookie anterior no permite recuperarla.

Las lecturas de negocio usan Spring Data JPA con Specifications del evaluador, incluso al buscar por identificador. El backend solo expone un DTO con los campos que autoriza `camposLegibles`; los cuerpos de escritura se restringen con DTOs y `camposEscribibles`. Los datos internos de identidad/permisos se consultan para construir el sujeto antes de autorizar; no se exponen como consultas de la API. El cliente no elige usuario, empresa ni rol para leer su cuenta.

El PEP exige `@RequiereAccion` y consulta `puedeInvocar` antes del handler. Registro usa `cuenta.registrar`, lectura de cuenta usa `cuenta.gestionar`, reautenticación usa `sesion.reautenticar` y cambio de rol usa `staff.cambiar_rol`. Un filtro aplica `sesion.iniciar` y `sesion.cerrar` antes de los filtros de login/logout de Spring. Una ruta sin acción declarada o sin permiso se deniega; los errores del evaluador tampoco conceden acceso.

`GET /api/auth/csrf` es infraestructura de Spring Security implementada en el filtro, no un controller de negocio sin anotación. Solo entrega el token antifalsificación de la sesión. `/error` y los despachos de error están habilitados sin detalles internos. CSRF y `anyRequest().denyAll()` se mantienen.

La prueba de reautenticación se guarda del lado del servidor, vinculada al usuario, empresa y rol de la sesión. Vence a los cinco minutos (`security.reauthentication-validity`) y una contraseña incorrecta invalida la confirmación previa. Las reglas de reautenticación y exclusión de la propia membresía están en el evaluador. Las denegaciones del PEP, filtros, handlers y campos inválidos se comunican a `RegistroAccesos`, cuyo almacenamiento definitivo corresponde a R18/R19.

La página protegida del frontend es `/mi-cuenta`. Su guardia consulta `/api/account` al cargar y al recuperar el foco; el servidor aplica la protección independientemente del navegador. **Verificar acceso** realiza una nueva consulta al backend.

## Referencias de implementación

- [Spring Security: CSRF e integración con aplicaciones JavaScript](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
- [Spring Security: persistencia de autenticación y sesiones](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html).

Las decisiones de dominio y alta de cuentas provienen del requisito del proyecto; esas referencias sustentan el uso de los mecanismos del framework.
