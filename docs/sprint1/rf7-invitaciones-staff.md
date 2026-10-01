# RF7: invitaciones y roles del staff

El Administrador puede invitar por correo, listar el staff de su empresa y asignar **Administrador**, **Reclutador** o **Editor de contenido**. La invitación crea una membresía pendiente sin rol. Aceptarla establece nombre, apellido y contraseña, activa la membresía y consume el token; no inicia sesión ni concede permisos. Hasta recibir un rol, el invitado no puede ingresar.

## Probar en desarrollo

1. Ejecutar el backend con el perfil `dev` y el frontend, como indica el README. Iniciar sesión con un Administrador creado mediante `scripts/crear-empresa.ps1`.
2. Desde **Mi cuenta → Gestionar staff**, ingresar un correo nuevo y enviar la invitación.
3. En desarrollo no se envían correos: abrir el archivo nuevo en `backend/.data/invitaciones/`. Contiene el destinatario y el enlace. Abrirlo en otro navegador o ventana privada y completar la aceptación.
4. Volver al Administrador y pulsar **Actualizar miembros**. El invitado aparece activo y **Sin rol**. Asignar **Reclutador** y confirmar la contraseña del Administrador.
5. Iniciar sesión con el invitado. Los permisos permiten `oferta.gestionar`, `postulacion.ver_listado` y las transiciones válidas de `postulacion.cambiar_estado`, solamente en su empresa. Como estos módulos aún no tienen endpoints, esta parte se comprueba con las pruebas de autorización.
6. Cambiarlo a **Editor de contenido**, confirmando nuevamente la contraseña. En la sesión del invitado volver a Mi cuenta o enfocar la ventana: podrá entrar a **Gestionar cursos y proyectos**, y pierde los permisos de reclutamiento inmediatamente.
7. Verificar que no pueda entrar a `/staff`, invitar ni asignar roles. El backend rechaza estas operaciones aunque se invoquen directamente.
8. También se puede asignar **Administrador** a un miembro activo. Nadie puede modificar su propio rol. Un Administrador de otra empresa tampoco puede modificar ese miembro.

Si una invitación pendiente venció, invitar nuevamente al mismo correo desde la misma empresa reemplaza el enlace anterior. Una cuenta existente de otra empresa o un Talento nunca se convierte ni se reasigna. La respuesta de envío es genérica.

## API

| Método y ruta | Acción | Datos |
|---|---|---|
| `GET /api/staff` | `staff.ver` | Miembros de la empresa, sin secretos |
| `POST /api/staff/invitations` | `staff.invitar` | `correo` |
| `POST /api/invitations/accept` | `invitacion.aceptar` | `nombre`, `apellido`, `password`; encabezado `X-Invitation-Token` |
| `POST /api/auth/reauthenticate` | `sesion.reautenticar` | `password` del Administrador |
| `PATCH /api/staff/{usuarioId}/rol` | `staff.cambiar_rol` | `rol`: `ADMIN`, `RECLUTADOR` o `EDITOR` |

Todas las escrituras requieren CSRF, incluida la aceptación anónima. Obtener el encabezado y token mediante `GET /api/auth/csrf`, conservando la cookie. El PEP valida la capacidad de aceptación sin agregar permisos al catálogo de roles. La aceptación vuelve a verificar el token dentro de una transacción con bloqueo para evitar su uso simultáneo. Las demás operaciones pasan por el evaluador y sus filtros de organización.

Los tokens tienen 256 bits aleatorios, vencen a las 48 horas y solo se guarda su SHA-256 en la membresía. No se devuelven en respuestas de la API ni se imprimen en logs. El enlace usa un fragmento que el navegador retira de la barra al abrirlo. Un fallo de entrega revierte la creación de la cuenta y la invitación.

## Entrega por correo

Sin el perfil `dev`, configurar `PUBLIC_APP_URL` (URL HTTPS del frontend), `MAIL_FROM` y las propiedades estándar de Spring Mail mediante variables de entorno: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` y `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, según el servidor SMTP. Si SMTP no está configurado, el envío responde 503 y revierte los cambios; no simula una entrega exitosa.

En `dev`, `app.invitations.directory` permite cambiar la carpeta local. Sus archivos contienen enlaces secretos para pruebas, no se sirven por HTTP y están ignorados por Git. `PUBLIC_APP_URL` vale `http://localhost:5173` por defecto; ajustarlo si se usa otro puerto.

## Pruebas

Desde `backend`:

```powershell
.\mvnw.cmd test
```

`StaffInvitationIntegrationTest` recorre invitación, aceptación, asignación de los tres roles, cambio sobre la misma sesión, aislamiento entre empresas, CSRF, campos no permitidos, expiración, reenvío, uso único y concurrente, membresía pendiente, cuenta inactiva y fallo de entrega. El envío se sustituye por un mock: las pruebas no mandan correos reales. Las pruebas previas mantienen las verificaciones de reautenticación, autoasignación y permisos mínimos.
