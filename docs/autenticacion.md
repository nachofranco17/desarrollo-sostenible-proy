# Registro e inicio de sesión

## Alcance

Se toma el informe `InformeProyecto.pdf` como contexto del proyecto XPerience. Para el alta de cuentas prevalece el requisito actualizado: **registro público de Talentos**, alta de empresas/Administradores por script y resto del staff por invitación RF7. No se implementa el registro público de empresas mencionado en la versión anterior del informe.

RF7 es un requisito independiente. Esta entrega incluye su estructura de usuario STAFF/membresía y el inicio de sesión de todos los roles activos, pero no envía invitaciones ni incluye pantallas de gestión del staff. Las pruebas de Reclutador y Editor crean fixtures que representan una invitación ya aceptada con rol asignado.

## Contrato HTTP

Todas las respuestas de error usan `{ "message": "..." }`. La sesión se conserva mediante cookie; el cliente debe enviarla en solicitudes posteriores.

| Método y ruta | Entrada | Resultado |
|---|---|---|
| `GET /api/auth/csrf` | Sin autenticación | 200: `token` y `headerName`; crea una sesión anónima si hace falta |
| `POST /api/auth/register` | JSON: `correo`, `nombre`, `apellido`, `password` | 202 con mensaje genérico tanto si crea como si ya existe |
| `POST /api/auth/login` | Form URL encoded: `correo`, `password` | 204 y sesión autenticada; 401 genérico si falla |
| `GET /api/account` | Sesión activa y permiso `cuenta.ver` | 200: datos propios, tipo, rol y empresa; 401 sin sesión; 403 al revocar acceso |
| `POST /api/auth/logout` | Cookie de sesión y CSRF | 204; elimina la sesión y la cookie |

Antes de cada POST, obtener el token de `/api/auth/csrf` y enviarlo en el header cuyo nombre devuelve esa respuesta (`X-CSRF-TOKEN`). Repetir después del login/logout, ya que cambia el token. Sin CSRF válido el servidor devuelve 403. El frontend ya realiza este flujo.

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
- `empresa` y `membresia`: cada usuario STAFF pertenece a una empresa; rol ADMIN, RECLUTADOR o EDITOR. La membresía debe estar activa y tener rol para iniciar sesión.
- `permiso`: acceso a `cuenta.ver` para los cuatro roles. La sesión contiene la identidad, y la autorización vuelve a consultar la base en cada acceso.
- `alta_empresa`: registro de altas hechas mediante el script, con empresa, Administrador, ejecutor y fecha; no guarda contraseñas. No sustituye a la auditoría completa de R18/R19.
- `SPRING_SESSION*`: sesiones JDBC. El identificador cambia al autenticar; logout elimina la sesión y una cookie anterior no permite recuperarla.

Las consultas se parametrizan. El backend solo expone un DTO con los campos permitidos de la cuenta propia. El cliente no elige usuario, empresa ni rol para esa consulta. Las rutas nuevas quedan cerradas hasta agregar una regla explícita en Spring Security.

La página protegida del frontend es `/mi-cuenta`. Su guardia consulta `/api/account` al cargar y al recuperar el foco; el servidor aplica la protección independientemente del navegador. **Verificar acceso** realiza una nueva consulta al backend.

## Referencias de implementación

- [Spring Security: CSRF e integración con aplicaciones JavaScript](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
- [Spring Security: persistencia de autenticación y sesiones](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html).

Las decisiones de dominio y alta de cuentas provienen del requisito del proyecto; esas referencias sustentan el uso de los mecanismos del framework.
