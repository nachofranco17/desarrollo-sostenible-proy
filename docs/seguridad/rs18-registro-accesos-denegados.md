# RS18 — Registro de los intentos de acceso denegados

Requerimiento de seguridad: **RS18** (ASVS v5.0.0, documentado como R18 en el modelo)
Versión: 1.1 (06/10/2026)

Todo intento de acceso denegado por autorización queda registrado en un evento estructurado (JSON), con metadatos suficientes para reconstruir el hecho y sin datos sensibles.

## Dónde se genera el evento

El punto único de emisión es `Denegaciones.registrar`, invocado por:

- el `accessDeniedHandler` y el `authenticationEntryPoint` de Spring Security;
- el filtro de sesión (`SessionPolicyFilter`) cuando deniega login/logout;
- el manejador de fallos de login;
- `ApiErrors` ante campos no permitidos / cuerpos inválidos de autorización (RS12).

Ese método completa metadatos seguros y llama a `RegistroAccesos.denegado`. La implementación activa es `RegistroAccesosEstructurado`, que escribe una línea JSON en el logger `uy.edu.um.xperience.security.access`.

No se agregan `logger.warn` sueltos en cada endpoint: las denegaciones que ya pasan por el mecanismo de autorización existente quedan registradas de forma consistente.

## Formato del evento

Una línea de log cuyo mensaje es un objeto JSON parseable. Ejemplo:

```json
{
  "event": "authorization_denied",
  "timestamp": "2026-10-02T20:00:00Z",
  "userId": "uuid-del-usuario",
  "origin": "127.0.0.1",
  "resourceType": "profile",
  "resourceId": "uuid-del-recurso",
  "operation": "read",
  "action": "perfil.gestionar",
  "result": "denied",
  "reason": "INSTANCE_ACCESS_DENIED",
  "field": "rol"
}
```

`field` solo aparece en denegaciones de campo (RS12). `resourceId` solo si hay un UUID seguro en la ruta.

## Campos registrados

| Campo | Contenido |
|---|---|
| `event` | Siempre `authorization_denied` |
| `timestamp` | ISO-8601 (`Instant`) |
| `userId` | UUID del usuario autenticado, o `null` si es visitante/anónimo |
| `origin` | IP remota resuelta por el contenedor (`HttpServletRequest.getRemoteAddr`) |
| `resourceType` | Metadato estable: `profile`, `course`, `offer`, `application`, `file`, `account`, `membership`, `session`, etc. |
| `resourceId` | UUID técnico del recurso cuando figura en la ruta |
| `operation` | Verbo estable: `read`, `update`, `create`, `delete`, `download`, `list`, `publish` |
| `action` | Acción de política ya usada por el PEP (`perfil.gestionar`, `material.descargar`, …) |
| `result` | Siempre `denied` |
| `reason` | Categoría genérica de denegación |
| `field` | Nombre técnico del campo rechazado (solo RS12) |

## Campos que deliberadamente NO se registran

- Contraseñas (actual, nueva, hash)
- Tokens, JWT, cookies, header `Authorization`
- Cuerpo completo de la request
- Email, teléfono, nombre u otros datos personales
- Contenido del recurso (perfil, curso, respuestas, currículum)
- Contenido de archivos, bytes, rutas físicas de almacenamiento
- URL firmadas o query strings con firmas
- Headers completos
- Valor enviado de un campo denegado

El evento se construye con una allowlist explícita; no se serializa el request ni el sujeto completo.

## Categorías de denegación (`reason`)

| Categoría | Cuándo |
|---|---|
| `ROLE_PERMISSION_DENIED` | El PEP o el filtro de sesión deniegan por rol/acción (RS9) |
| `INSTANCE_ACCESS_DENIED` | Acceso horizontal a instancia (p. ej. perfil ajeno por id) (RS11) |
| `ORGANIZATION_ACCESS_DENIED` | Recurso de otra empresa (cursos/staff/ofertas y postulantes anidados) (RS15) |
| `FIELD_ACCESS_DENIED` | Campo no autorizado en el cuerpo (RS12) |
| `FILE_ACCESS_DENIED` | Descarga/operación de material o currículum sin autorización (RS14) |

## Tratamiento de IP / origen

Se usa `request.getRemoteAddr()`, la IP que ya resuelve el contenedor servlet. No se leen headers controlados por el cliente (`X-Forwarded-For`, etc.) para no confiar ciegamente en ellos. Si en el futuro hay proxy confiable, la configuración de Tomcat/Spring debe resolver la IP remota; RS18 no implementa lógica propia de proxies.

## Comportamiento ante fallo del logger

`Denegaciones.registrar` y `RegistroAccesosEstructurado.denegado` atrapan cualquier error de registro. La denegación original sigue su curso (401/403/400). Un fallo al emitir el evento **nunca** convierte la operación en permitida (fail-closed respecto del acceso).

## Pruebas

`Rs18DeniedAccessLogTest`:

1. **RS11 horizontal:** Talento A pide `/api/profile/{idB}` → 403, un evento con `INSTANCE_ACCESS_DENIED`.
2. **RS9 vertical:** Reclutador pide un curso → 403, evento con `ROLE_PERMISSION_DENIED`.
3. **RS15 entre empresas:** Editor de B pide curso de A → 403, evento con `ORGANIZATION_ACCESS_DENIED`.
4. **RS12 campo:** PATCH de perfil con `rol` (y secretos en el body) → 400, evento con `FIELD_ACCESS_DENIED` y `field=rol`; el valor y los secretos no aparecen.
5. **RS14 archivo:** Editor de B descarga material de A → 403, evento con `FILE_ACCESS_DENIED`; sin contenido, URL firmada ni credenciales.

6. **Fallo del logger:** `Rs18LoggerFailureTest` — si `RegistroAccesos.denegado` lanza, la solicitud sigue denegada (403); nunca fail-open.

7. **RF6:** denegaciones de oferta, listado empresarial, perfil contextual y CV adjunto comprueban categorías de empresa/archivo, operaciones `list`/`publish`/`download` y ausencia de información de perfil o almacenamiento.

Todas comprueban usuario, timestamp ISO-8601, origen, recurso, operación, formato JSON y un solo evento por intento.

```powershell
cd backend
.\mvnw.cmd "-Dtest=Rs18DeniedAccessLogTest,Rs18LoggerFailureTest" test
```

## Cómo probar manualmente

1. Arrancar el backend y provocar cada denegación (mismo flujo que las pruebas).
2. Observar el log de la aplicación (logger `uy.edu.um.xperience.security.access`) y verificar una línea JSON por intento con los campos de la tabla.
3. Confirmar que en esa línea no aparecen contraseñas, emails, tokens, cookies, cuerpos ni contenido de archivos.

## Alcance y pendientes

RS18 solo **emite** el evento. La protección del almacén de logs (RS19), retención, SIEM o dashboards quedan fuera de alcance.

RF6 usa el mismo mecanismo para ofertas, postulaciones, perfil contextual y CV. Las rutas empresariales se clasifican como organización y las operaciones de CV como archivo, salvo que ya se haya marcado una denegación por rol o campo. Las búsquedas/filtros no se incluyen en el evento. Inscripción sigue pendiente de API de negocio.
