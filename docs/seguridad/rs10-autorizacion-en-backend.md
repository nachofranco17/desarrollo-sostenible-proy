# RS10 — Decisión de autorización exclusiva en el backend

Requerimiento de seguridad: **RS10** (ASVS v5.0.0-8.3.1; documentado como R10 en el modelo)
Versión: 1.0 (16/10/2026)

Las reglas de autorización se aplican en una capa de servicio confiable: el backend. Ninguna decisión depende de controles que un consumidor no confiable puede manipular, como el JavaScript del navegador, botones ocultos, rutas del frontend, headers, cookies o campos agregados a la solicitud.

## Dónde se decide

| Nivel | Componente | Datos que usa |
|---|---|---|
| Función (¿puede invocar la acción?) | `PolicyEnforcementPoint` sobre toda ruta `/api/**` | Acción declarada con `@RequiereAccion` en el handler + sujeto de la sesión |
| Login / logout | `SessionPolicyFilter`, antes de los filtros de Spring Security | Mismo evaluador que el PEP |
| Instancia (¿sobre este recurso?) | Servicios de cada módulo + `EvaluadorPolitica.autorizar` / `filtrar` | Consulta acotada por dueño, empresa o publicación tomados del sujeto |
| Campos | `camposLegibles` / `camposEscribibles` + parsers con lista blanca | Acción, sujeto y estado del recurso |
| Fuera de `/api` | `anyRequest().denyAll()` | — |

El **sujeto** (`Sujeto`: usuario, tipo de cuenta, empresa y rol) lo arma `Sujetos` en cada solicitud a partir del `usuarioId` guardado en la sesión del servidor (Spring Session JDBC). No existe ningún camino por el que el cliente lo declare: no hay JWT ni token autocontenido, y la cookie `SESSION` es solo un identificador opaco.

## Señales del cliente que se ignoran

| Señal | Por qué no tiene efecto |
|---|---|
| Headers como `X-Role`, `X-User-Id`, `X-Empresa-Id`, `X-Forwarded-User`, `X-Permissions` | Ningún handler ni filtro los lee. La única excepción es `X-Invitation-Token` en `invitacion.aceptar`, que valida el PEP contra el hash guardado en la base |
| `Authorization: Bearer …` | No hay autenticación por token; Spring Security no lo procesa |
| Parámetros `?rol=`, `?empresaId=`, `?usuarioId=`, `?admin=` | Los handlers no los declaran; los que no esperan parámetros responden 400 (`OfferQuery.requireEmpty`) |
| Cookies `rol`, `empresaId`, `usuarioId` o una `SESSION` inventada | Solo se reconoce una sesión existente en el servidor; una cookie inventada equivale a visitante (401) |
| Campos `rol`, `empresaId`, `usuarioId`, `talentoId`, `estado` en el body | `fail-on-unknown-properties=true` en los DTO y listas blancas en los parsers de JSON: la solicitud entera se rechaza con 400 (RS12) |
| Rol mostrado en el frontend (`GET /api/account`) | Es informativo. Modificarlo en memoria o en DevTools no cambia lo que el servidor permite |

## El frontend solo adapta la interfaz

El frontend oculta o deshabilita opciones según el `rol` que devuelve `GET /api/account`, para no ofrecer acciones que van a fallar. Cada uno de esos controles tiene su verificación en el backend:

| Control en la interfaz | Verificación en el backend |
|---|---|
| Rutas `/staff`, `/gestion`, `/gestion-ofertas`, `/perfil`, `/inscripciones` según el rol | PEP: `puedeInvocar` sobre la acción del endpoint |
| No ofrece cambiar el propio rol ni darse de baja | Evaluador: `staff.cambiar_rol` / `staff.dar_baja` exigen que la membresía objetivo no sea la del sujeto |
| Pide la contraseña antes de cambiar un rol o dar de baja | Evaluador: exige una reautenticación válida, vinculada a la sesión (RS23) |
| Oculta "Eliminar" en lecciones/entregables de un curso publicado | `CourseService`: solo se eliminan en borrador (409) |
| Deshabilita la edición de un curso dado de baja | `CourseService.requireModifiable` (409) |
| Muestra una oferta publicada solo para lectura | `camposEscribibles` no devuelve campos fuera de `BORRADOR` (409) |
| El catálogo solo lista contenido publicado | `EnrollmentService`: inscripción solo sobre un curso publicado + `autorizar` (403) |
| Deshabilita "Postularme" sin currículum o si ya se postuló | `ApplicationService`: exige CV actual y rechaza duplicados (409) |
| Límite de 100 cursos requeridos en una oferta | Validación de `OfferInput` (400) |

El frontend no guarda el rol, la empresa ni permisos en `localStorage`, `sessionStorage` o cookies, y no envía headers de identidad. Las llamadas viajan solo con la cookie de sesión, el token CSRF y, en la aceptación de invitaciones, el token de invitación.

## Regla para funcionalidades nuevas

1. Toda decisión de acceso se toma en el backend, con el sujeto de `Sujetos` y el evaluador. Nunca con un dato de la solicitud que describa al usuario o sus permisos.
2. Un handler no lee headers ni cookies para decidir acceso, y no recibe parámetros, variables de ruta ni campos del body con nombres como `rol`, `empresaId`, `usuarioId`, `talentoId` o `permisos`. La excepción es `staff.cambiar_rol`, que recibe el rol **a asignar** a otro miembro.
3. Si el frontend oculta o deshabilita una opción, la regla equivalente tiene que existir en el backend y tener una prueba que la invoque directamente contra la API.

## Pruebas

`Rs10BackendAuthorizationTest` levanta un servidor HTTP real y llama a la API directamente, sin pasar por la interfaz:

1. Un Talento invoca operaciones de staff, cursos y ofertas con headers (`X-Role`, `X-User-Id`, `X-Empresa-Id`, `Authorization` con un token inventado…), parámetros (`?rol=ADMIN&empresaId=…`) y cookies falsificadas. La respuesta es la misma 403 que sin ellas y no se crea ni modifica nada.
2. Un Reclutador no amplía su rol dentro de su propia empresa con las mismas señales.
3. Un visitante con headers de identidad, cookies de rol o una cookie `SESSION` inventada recibe 401 y la respuesta no expone datos del usuario suplantado.
4. Las restricciones que la interfaz expresa ocultando botones (propio rol, baja propia, cambio sin reautenticación) se rechazan en el servidor. Un control positivo confirma que el mismo pedido es válido cuando el servidor lo autoriza.
5. Las reglas de estado que la interfaz expresa deshabilitando controles (eliminar lección publicada, editar curso dado de baja, inscribirse a un borrador, editar una oferta publicada, postularse sin CV) se rechazan sin cambios en la base.
6. **Guarda estática del backend:** recorre todos los handlers y falla si alguno lee un header (salvo el token de invitación), una cookie, o recibe un parámetro, variable de ruta o campo de body con nombre de atributo del sujeto.
7. **Guarda estática del frontend:** recorre `frontend/src` y falla si aparece `localStorage`, `sessionStorage`, `indexedDB`, `document.cookie`, un header `Authorization` o headers de identidad (`X-Role`, `X-User…`, `X-Empresa…`).

Las guardas 6 y 7 se verificaron con un fallo intencional: agregar `@RequestHeader("X-Role")` a un handler y una línea con `localStorage` en `api.ts` hace fallar cada una con el lugar exacto de la violación.

Cobertura relacionada ya existente: `PolicyContractTest` (PEP, denegación por defecto, campos prohibidos), `AuthIntegrationTest` (campos de privilegio en el registro, reautenticación), `OfferIntegrationTest` (`?empresaId=` y campos prohibidos), `Rs11InstanceAuthorizationTest` y `Rs13IdentifierNotAccessKeyTest`.

```bash
cd backend
./mvnw "-Dtest=Rs10BackendAuthorizationTest" test
```

## Cómo probar manualmente

1. Iniciar sesión como Talento en el frontend.
2. En DevTools, consola, ejecutar una operación que la interfaz no ofrece:
   ```js
   const { token, headerName } = await (await fetch('/api/auth/csrf')).json();
   await fetch('/api/company/offers', { method: 'POST',
     headers: { 'Content-Type': 'application/json', [headerName]: token, 'X-Role': 'ADMIN' },
     body: JSON.stringify({ titulo: 'x', descripcion: 'x', cursosRequeridos: [] }) });
   ```
   → 403 `{"message":"Acceso denegado."}`.
3. Repetir con `?rol=ADMIN` en la URL, o agregando `document.cookie = 'rol=ADMIN'` antes → mismo 403.
4. Navegar a `/gestion-ofertas` escribiendo la URL: el frontend redirige; aunque se fuerce el render, `GET /api/company/offers` responde 403.
5. Con Postman o curl sin cookie de sesión y con `X-User-Id` de un administrador → 401.
