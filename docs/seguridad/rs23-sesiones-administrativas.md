# RS23 — Reautenticación y sesiones administrativas

En **Mi cuenta → Gestionar staff**, iniciar **Asignar rol** o **Dar de baja** abre una verificación de identidad. Cada operación requiere volver a ingresar la contraseña del administrador. **Cancelar** cierra la verificación sin enviar la operación. Una contraseña incorrecta tampoco aplica cambios.

El backend exige una prueba de reautenticación vinculada al usuario, empresa y rol, con vigencia de cinco minutos. Ambas operaciones validan permisos y membresía activa, restringen el objetivo a la misma empresa y prohíben operar sobre el propio administrador. La baja marca la membresía como `BAJA`: impide nuevos ingresos y deniega el acceso de sesiones existentes.

## Eliminación de la propia cuenta

El Admin no puede eliminar su propia cuenta: `DELETE /api/account` responde **403** antes de modificar datos o invalidar su sesión, incluso después de reautenticarse y aunque haya otros administradores. Esto aplica a la cuenta la misma prohibición de modificar el rol o estado de la propia membresía de [restricciones-campo.md](restricciones-campo.md). La interfaz informa la restricción y no ofrece el botón de eliminación al Admin.

Talento, Reclutador y Editor conservan la posibilidad de eliminar su propia cuenta, pero deben ingresar su contraseña en la confirmación de **Mi cuenta**. La interfaz primero invoca `POST /api/auth/reauthenticate` y solo tras su éxito envía `DELETE /api/account`. Cancelar o ingresar una contraseña incorrecta no envía la eliminación. El campo de contraseña se vacía al enviar el formulario y se descarta al cancelar.

La eliminación usa la acción independiente `cuenta.eliminar` con alcance `PROPIO`, sin permiso para Admin. `AuthorizationService` exige la prueba en `filtrar` y `autorizar`, y prohíbe la autoeliminación administrativa incluso ante una concesión accidental del permiso. `ProtectedAccounts.delete` consulta y autoriza con esta acción dentro de la transacción. `cuenta.gestionar` conserva la lectura de la cuenta sin exigir contraseña. La migración V11 incorpora los permisos de eliminación y reautenticación para los otros tres roles, sin reescribir migraciones aplicadas.

Sin prueba, con prueba vencida, de otro usuario, empresa, rol o sesión, el servidor deniega con **403** sin desactivar la cuenta. La prueba requiere el CSRF de la sesión, se conserva solo en el servidor y dura cinco minutos. Una contraseña incorrecta elimina una prueba anterior. Tras una baja autorizada, la cuenta queda inactiva, la sesión actual se invalida y las demás pierden acceso en su siguiente solicitud.

## Duración de las sesiones

Las sesiones de rol `ADMIN` expiran tras **10 minutos de inactividad**; las demás, tras **30 minutos**. Cada solicitud renueva la actividad. Los límites se aplican al iniciar sesión y se actualizan según el rol vigente en solicitudes posteriores.

Configuración: `security.admin-session-timeout=10m`, `spring.session.timeout=30m`, `security.reauthentication-validity=5m`. El límite administrativo debe ser positivo y menor que el común; una configuración inválida impide iniciar la aplicación.

## Verificación

1. Abrir una sesión administrativa y elegir otro miembro activo. Iniciar un cambio de rol y cancelar; comprobar que conserva su rol. Repetir con **Dar de baja** y comprobar que sigue activo.
2. Confirmar cada operación con una contraseña incorrecta y comprobar que no se aplica. Con la contraseña correcta, comprobar el cambio o la baja y que el miembro dado de baja pierde el acceso.
3. Abrir sesiones de administrador y talento en navegadores separados. Dejar ambos sin solicitudes durante 11 minutos. Al volver a solicitar un recurso protegido, el administrador debe recibir 401 y el talento debe conservar acceso. La página puede permanecer visible hasta que haga una solicitud al servidor.
4. Como Admin, invocar `DELETE /api/account` con cookie y CSRF antes y después de reautenticarse: ambas solicitudes deben devolver 403. La cuenta debe seguir activa y debe conservar acceso a la gestión de staff. Repetir con otro Admin activo en la empresa: el resultado también debe ser 403.
5. Como Talento, Reclutador o Editor, intentar eliminar sin reautenticación: 403 y cuenta activa. Confirmar con contraseña incorrecta: no se elimina. Reautenticarse correctamente y repetir desde otra sesión: 403. En la sesión reautenticada, eliminar con CSRF: 204, cuenta inactiva y demás sesiones revocadas en la próxima solicitud.

Pruebas: `AuthIntegrationTest` verifica permisos, contraseña, revocación y expiración con Spring Session JDBC, simulando la misma inactividad en ambas sesiones. También comprueba la autoeliminación administrativa denegada con uno o varios administradores y la eliminación reautenticada de los otros tres roles. `PolicyContractTest` verifica pruebas vencidas, futuras o de otra identidad y la denegación al invocar directamente el servicio. `frontend/e2e/rs16.spec.ts` y `rs23.spec.ts` verifican confirmación, contraseña incorrecta, cancelación y ausencia de autoeliminación para Admin con respuestas controladas; `staff.spec.ts` cubre la gestión integrada del staff.

Validación del 03/11/2026: 274 pruebas de backend aprobadas (`AuthIntegrationTest`, `PolicyContractTest`, `AuthorizationMatrixTest`, `LeastPrivilegeTest`, `PolicyMigrationTest` y `EndpointConventionTest`), compilación del frontend y 3 pruebas de navegador de RS16/RS23 aprobadas.
