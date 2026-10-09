# RS23 — Reautenticación y sesiones administrativas

En **Mi cuenta → Gestionar staff**, iniciar **Asignar rol** o **Dar de baja** abre una verificación de identidad. Cada operación requiere volver a ingresar la contraseña del administrador. **Cancelar** cierra la verificación sin enviar la operación. Una contraseña incorrecta tampoco aplica cambios.

El backend exige una prueba de reautenticación vinculada al usuario, empresa y rol, con vigencia de cinco minutos. Ambas operaciones validan permisos y membresía activa, restringen el objetivo a la misma empresa y prohíben operar sobre el propio administrador. La baja marca la membresía como `BAJA`: impide nuevos ingresos y deniega el acceso de sesiones existentes.

Las sesiones de rol `ADMIN` expiran tras **10 minutos de inactividad**; las demás, tras **30 minutos**. Cada solicitud renueva la actividad. Los límites se aplican al iniciar sesión y se actualizan según el rol vigente en solicitudes posteriores.

Configuración: `security.admin-session-timeout=10m`, `spring.session.timeout=30m`, `security.reauthentication-validity=5m`. El límite administrativo debe ser positivo y menor que el común; una configuración inválida impide iniciar la aplicación.

## Verificación

1. Abrir una sesión administrativa y elegir otro miembro activo. Iniciar un cambio de rol y cancelar; comprobar que conserva su rol. Repetir con **Dar de baja** y comprobar que sigue activo.
2. Confirmar cada operación con una contraseña incorrecta y comprobar que no se aplica. Con la contraseña correcta, comprobar el cambio o la baja y que el miembro dado de baja pierde el acceso.
3. Abrir sesiones de administrador y talento en navegadores separados. Dejar ambos sin solicitudes durante 11 minutos. Al volver a solicitar un recurso protegido, el administrador debe recibir 401 y el talento debe conservar acceso. La página puede permanecer visible hasta que haga una solicitud al servidor.

Pruebas: `AuthIntegrationTest` verifica permisos, contraseña, revocación y expiración con Spring Session JDBC, simulando la misma inactividad en ambas sesiones. `frontend/e2e/rs23.spec.ts` verifica el flujo de confirmación y cancelación con respuestas controladas; `staff.spec.ts` cubre el flujo integrado.
