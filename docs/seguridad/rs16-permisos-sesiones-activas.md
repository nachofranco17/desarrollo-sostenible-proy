# RS16 — Efecto inmediato en sesiones activas

La sesión almacena la identidad del usuario, no sus permisos de negocio. `Sujetos` resuelve el rol y la empresa desde la base; `AuthorizationService` verifica que la cuenta y la membresía sigan habilitadas y consulta los permisos vigentes para cada evaluación. Un cambio confirmado por otra sesión se refleja en la siguiente solicitud, sin recargar la página ni iniciar sesión otra vez.

No existe un caché de permisos entre solicitudes ni un TTL de autorización. Los cachés de segundo nivel y de consultas de Hibernate están desactivados explícitamente. Por eso no hay una entrada que invalidar al cambiar el rol, la membresía o la cuenta: la siguiente evaluación consulta el estado persistido. El caché del navegador tampoco conserva respuestas protegidas (`Cache-Control: no-store`).

- **Cambio de rol:** se conserva la sesión y se aplican los permisos nuevos, tanto los otorgados como los retirados.
- **Baja del staff:** se marca la membresía `BAJA`. La siguiente solicitud protegida devuelve 403 e invalida esa sesión; reutilizar su cookie devuelve 401. Esto se aplica a cada sesión abierta del miembro.
- **Eliminación de cuenta:** `DELETE /api/account`, con CSRF, elimina lógicamente la propia cuenta (`usuario.activo=false`) y cierra la sesión que hizo la operación. Las demás sesiones pierden acceso en su siguiente solicitud protegida, igual que en la baja. Se preservan los datos relacionados; esta operación no borra físicamente los registros ni permite eliminar cuentas ajenas.

La pantalla **Mi cuenta → Eliminar mi cuenta** pide confirmar. Cancelar no envía la eliminación. Una pantalla que ya está visible puede conservar datos previamente recibidos; el servidor valida los permisos al recibir cada nueva solicitud.

## Prueba manual

1. Abrir la sesión de un Reclutador de E1 y, en otro navegador, la del administrador de E1.
2. Desde el navegador del Reclutador, ejecutar `fetch('/api/company/courses', { cache: 'no-store' }).then(r => r.status)` en la consola: devuelve 403.
3. Cambiar su rol a Editor desde **Gestionar staff**. Sin recargar el navegador del miembro, repetir exactamente la solicitud: devuelve 200. Volver a Reclutador: devuelve 403. La sesión sigue abierta.
4. Dar de baja al miembro desde la sesión administrativa. En el navegador del miembro, solicitar `/api/account`: devuelve 403; la siguiente devuelve 401.
5. Con otro miembro activo, abrir dos sesiones de su cuenta. Eliminarla desde **Mi cuenta** en una; solicitar `/api/account` en la otra sin recargar: devuelve 403 y luego 401. Ya no puede ingresar nuevamente.

## Pruebas automáticas

Los casos `rs16*` de `AuthIntegrationTest` usan sesiones HTTP reales y Spring Session JDBC: cambio de rol en ambos sentidos con la misma cookie, baja con dos sesiones, eliminación con dos sesiones y CSRF, y cambio directo de permisos después de repetir solicitudes para calentar cualquier posible caché. No esperan vencimientos ni introducen pausas para observar los cambios.

`frontend/e2e/rs16.spec.ts` prueba la confirmación, cancelación, envío de CSRF y salida de la cuenta mediante respuestas controladas.
