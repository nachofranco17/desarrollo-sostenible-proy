# Permisos iniciales y alineación con el diseño

La fuente de verdad es [docs/seguridad/modelo-autorizacion.md](seguridad/modelo-autorizacion.md), junto con la matriz y las restricciones de campos. La migración V4 reemplaza el catálogo reducido de V3 por sus 54 permisos, sin modificar usuarios ni membresías existentes.

## Comportamiento

- El Talento recibe el conjunto base documentado. Incluye catálogo publicado, perfil propio, inscripción y postulación, además de las acciones de avance, currículum y consulta propias previstas en la matriz.
- El staff sin rol asignado no puede iniciar sesión ni ejecutar acciones. La excepción anterior para leer su cuenta sin rol se eliminó para cumplir la condición general del documento.
- El Administrador inicial recibe el rol explícitamente mediante el script existente. Las membresías nuevas conservan rol nulo por defecto.
- Asignar Reclutador habilita sus operaciones sobre su empresa. También puede consultar catálogo y ofertas publicados de otras empresas, como exige la matriz; no puede gestionar esos recursos ajenos.
- Los permisos se vuelven a consultar en solicitudes posteriores. Reasignar un rol no acumula los permisos del anterior; dar de baja la cuenta/membresía o dejarla sin rol revoca el acceso de sesiones existentes.

El catálogo de permisos no implementa módulos de negocio. Ya existen cursos, perfiles editables e invitaciones del staff; ofertas, inscripciones y postulaciones siguen pendientes. Las decisiones sobre publicación/propiedad de módulos pendientes se prueban con atributos del servidor. Los alcances INSCRIPTO y POSTULANTE se deniegan hasta que existan sus relaciones y consultas reales; no se sustituyen por banderas que pueda enviar el cliente.

## Cambio de rol con reautenticación

Desde la sesión del Administrador, obtener CSRF con `GET /api/auth/csrf` y enviarlo en ambos pasos:

1. `POST /api/auth/reauthenticate` con `{ "password": "contraseña actual del Administrador" }`. Debe devolver 204.
2. `PATCH /api/staff/{usuarioId}/rol` con `{ "rol": "RECLUTADOR" }`. Debe devolver 204.

La confirmación dura cinco minutos y pertenece a esa sesión, usuario, empresa y rol. Una contraseña incorrecta devuelve 401 e invalida la confirmación anterior. Sin confirmación vigente, el cambio se deniega con 403. No hay cambio sobre la propia membresía ni sobre otra empresa. El servidor obtiene la empresa desde la base; no acepta ese campo en el cuerpo.

## Control de acceso

- Los endpoints de negocio declaran `@RequiereAccion`. El PEP comprueba el nivel de función antes del handler.
- Login y logout se asocian a `sesion.iniciar` y `sesion.cerrar` desde un filtro anterior a los de Spring Security.
- `EvaluadorPolitica` ofrece autorización de instancia, Specifications y campos legibles/escribibles. Las consultas de cuenta y membresía pasan por el filtro; las lecturas internas de identidad/permisos permiten construir el sujeto antes de autorizar.
- Ante acción desconocida, ausencia de permiso, recurso no soportado o error del evaluador, se deniega. Las escrituras son transaccionales.
- Las denegaciones y cuerpos con campos prohibidos invocan `RegistroAccesos`, sin contraseñas, cuerpos ni tokens. RS18 emite el evento estructurado (`RegistroAccesosEstructurado`); la protección del almacén (RS19) sigue pendiente. Ver [rs18-registro-accesos-denegados.md](seguridad/rs18-registro-accesos-denegados.md).

## Pruebas

### RS7: cuentas nuevas y asignación explícita

La protección ya forma parte del registro, RF7 y el evaluador de política. Las pruebas `rs7*` de `StaffInvitationIntegrationTest` verifican los tres escenarios usando cuentas creadas por HTTP y enlaces de invitación reales (con entrega de correo simulada):

1. **Talento recién registrado:** puede leer y editar su perfil. El evaluador permite catálogo, inscripción y postulación solo sobre recursos publicados y rechaza perfiles ajenos y operaciones sobre recursos de la empresa. Las rutas reales de gestión de cursos, listado de staff, invitación y asignación de roles devuelven 403.
2. **Invitado que aceptó, sin rol:** la membresía queda activa con rol nulo. No recibe ningún permiso del catálogo, no puede iniciar sesión y las operaciones de empresa por HTTP devuelven 401. Aceptar la invitación no equivale a asignar un rol.
3. **Asignación de Reclutador:** el Administrador se reautentica y asigna el rol por HTTP. Recién entonces el invitado puede ingresar. Se verifican los permisos de reclutamiento dentro de E1, el rechazo sobre E2 y la denegación de gestión de cursos y staff.

La matriz vigente también incluye acciones propias del Talento como consultar sus inscripciones y postulaciones o gestionar su currículum. Esas acciones no conceden acceso a recursos de la empresa ni a datos de otros Talentos. RS7 se comprueba manteniendo esa matriz; no se modifica el catálogo de permisos con estas pruebas.

Catálogo, inscripciones, ofertas y postulaciones todavía no tienen rutas de negocio implementadas: sus condiciones se prueban directamente sobre el evaluador. Perfil, cursos y staff se verifican además por HTTP. No se agregan módulos de negocio para probar RS7.

Para ejecutar únicamente los tres casos de aceptación:

```powershell
cd backend
.\mvnw.cmd '-Dtest=StaffInvitationIntegrationTest#rs7*' test
```

### Suite completa

```powershell
cd backend
.\mvnw.cmd test
```

Se verifican registro/login/logout por HTTP, permisos iniciales, membresías sin rol, asignación con reautenticación, revocación, consultas filtradas por usuario/empresa, expiración de la confirmación, campos prohibidos, PEP sin anotación, errores de política y llamadas al registro de denegaciones.

Una prueba contrasta la tabla de permisos con el documento; otra actualiza una base V3 y comprueba que conserva usuarios, hashes y membresías.
