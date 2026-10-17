# RS11 — Autorización por instancia de recurso

Requerimiento de seguridad: **RS11** (ASVS v5.0.0-8.2.2, documentado como R11 en el modelo)
Versión: 1.1 (06/10/2026)

Tener permiso para el **tipo** de recurso no habilita el acceso a cualquier instancia. Cada lectura o escritura por identificador debe comprobar la relación entre el consumidor autenticado y **esa** instancia.

## Controles implementados

La decisión de instancia se toma con el evaluador ya existente (`EvaluadorPolitica` / `AuthorizationService`):

1. El PEP (`@RequiereAccion`) solo resuelve el nivel de función (`puedeInvocar`).
2. El handler carga el recurso con una consulta ya acotada por atributos de la sesión (nunca por un `userId`/`empresaId` del body).
3. Sobre la instancia cargada (o un `ResourceAccess` construido en servidor) se llama a `autorizar`.
4. Si no hay fila o no hay permiso de instancia, la respuesta es la misma denegación (403), sin revelar existencia.

Alcances usados:

| Alcance | Relación |
|---|---|
| `PROPIO` | `recurso.ownerId == sujeto.usuarioId` (sesión) |
| `ORG` | `recurso.empresaId == sujeto.empresaId` (sesión) |
| `POSTULANTE` | Relación persistida postulación + oferta + empresa + Talento + CV adjunto |

`INSCRIPTO` sigue denegado mientras RF4 esté pendiente. `POSTULANTE` se implementa en RF6 mediante `ApplicantAccess`: el evaluador consulta la postulación persistida y comprueba todas las relaciones, sin aceptar flags del cliente. Las consultas JDBC acotadas más `autorizar` cumplen el mismo contrato que las Specifications de identidad/staff.

## Recursos cubiertos (funcionalidad existente)

| Recurso | Relación | Dónde se aplica |
|---|---|---|
| Perfil del Talento | `PROPIO` | `ProfileService`: carga por `sujeto.usuarioId`; `autorizar(..., perfil.gestionar, profile)` |
| Cuenta | `PROPIO` | `ProtectedAccounts` + `filtrar` (ya existente) |
| Membresía / staff | `ORG` | `StaffInvitationService` / `StaffRoleService` + `filtrar` (ya existente) |
| Curso / proyecto | `ORG` | `CourseService.owned`: `find(id, sujeto.empresaId)` + `autorizar` |
| Lección, entregable, material | Heredan el curso/proyecto | Consultas con `curso_id` + `empresa_id` de la sesión; id suelto no alcanza |
| Oferta | `ORG` para gestión; `PUBLICADO` para consulta | `OfferService`: empresa desde sesión o consulta publicada; autorización de instancia |
| Postulación propia | `PROPIO` | `ApplicationService`: consulta por Talento de sesión |
| Listado empresarial | `ORG` y `POSTULANTE` para el perfil | Oferta y empresa de sesión antes de filtros; perfil autorizado por postulación |
| CV actual | `PROPIO` | `CurriculumService`: referencia actual del Talento de sesión |
| Perfil/CV de postulante | `POSTULANTE` | Consulta anidada por postulación + oferta + empresa y verificación persistida del evaluador |

El evaluador reconoce `TalentProfile`, `Course`, `Offer`, `JobApplication` y `Curriculum` al mapear atributos de instancia. `ApplicantAccess` activa exclusivamente la verificación persistida de perfil/CV de un postulante.

## Endpoints tocados

- `GET/PATCH /api/profile/me` — identidad solo desde la sesión/`Sujeto`; sin rutas `/profile/{id}`.
- Todas las rutas de `/api/company/courses/**` — empresa solo desde el sujeto; nested resources exigen coincidencia de curso + empresa.

RF6 incorpora `/api/offers`, `/api/company/offers/**`, `/api/applications/me` y `/api/profile/me/curriculum`. Los perfiles y CV empresariales se consultan exclusivamente bajo `/api/company/offers/{id}/applications/{applicationId}/...`. No se agrega una descarga empresarial por UUID suelto de CV.

## Acceso horizontal

- Talento A no puede leer/escribir el perfil de B por URL (`/profile/{idB}`) ni inyectando ids en el body (campos desconocidos → 400; la identidad no cambia).
- Staff de la empresa B que conoce el UUID de un curso/entregable de la empresa A recibe 403 idéntico al de un UUID inexistente.
- Conocer el UUID de un entregable no permite operarlo bajo otro proyecto (`/courses/{proyectoB}/deliverables/{entregableDeA}` → 403).
- Una empresa no lista postulantes ni consulta perfil/CV de ofertas ajenas. Oferta/postulación ajena e inexistente producen la misma denegación.
- Una postulación de otra oferta, incluso de la misma empresa, no se puede abrir bajo una ruta con distinto padre.

## Camino indirecto

Hoy no existe API de inscripciones ni listado de inscriptos anidado bajo proyecto. Los caminos indirectos cubiertos son los recursos anidados de RF5 y RF6:

Proyecto autorizado → entregable / lección / material de **otro** proyecto (mismo o distinto UUID de curso en la ruta).

Cada instancia anidada se busca con `curso_id` de la ruta **y** `empresa_id` de la sesión. Acceder al proyecto padre no autoriza instancias hijas ajenas a ese padre.

Oferta autorizada → postulación/perfil/CV de otra oferta: la consulta exige también `oferta_id` y `empresa_id`. Tener acceso a una oferta no habilita otra postulación del mismo Talento ni su CV actual; la descarga usa la versión fijada en esa postulación.

Cuando existan inscripciones, el mismo patrón del modelo (`filtrar` / consulta con dueño) deberá aplicarse a `/inscripcion/{id}` y a cualquier listado anidado bajo proyecto; no se implementó ese módulo aquí.

## Recursos no cubiertos por falta de funcionalidad

| Recurso | Motivo |
|---|---|
| Inscripción | Sin tablas ni endpoints de negocio |
| Progreso (como recurso direccionable) | Solo campos vacíos derivados en el perfil; sin API propia |
| Entregable del Talento (responder / ver avance propio) | Solo gestión de empresa (RF5); sin RF4 |
| Camino proyecto → inscripción de otro Talento | Depende de inscripciones |

## Pruebas

`Rs11InstanceAuthorizationTest`:

1. Talento A no accede al perfil de B por URL ni por ids en el body; B no se modifica.
2. Contraprueba: A lee y edita su propio perfil.
3. El evaluador concede `perfil.gestionar` sobre el propio id y deniega el perfil/id de otro Talento.
4. Staff de otra empresa no alcanza curso ni entregable ajeno (403 = inexistente).
5. Camino indirecto: entregable de proyecto A bajo la ruta de proyecto B → 403; el vínculo en base no cambia.
6. Contraprueba: staff accede a los cursos de su empresa.
7. Las acciones propias no autorizan instancias ajenas a nivel de política; inscripción sigue pendiente de API y RF6 añade cobertura HTTP de postulaciones.

También siguen vigentes las pruebas de aislamiento en `ProfileIntegrationTest` y `CourseIntegrationTest`. RF6 incorpora pruebas de ofertas/postulaciones/CV por HTTP y `PolicyContractTest` comprueba que una relación inventada o manipulada no habilita `POSTULANTE`.

```powershell
cd backend
.\mvnw.cmd "-Dtest=Rs11InstanceAuthorizationTest" test
```

## Cómo probar manualmente

1. Registrar Talento A y Talento B. Con A autenticado:
   - `GET /api/profile/me` → 200 (propio).
   - `GET /api/profile/{id-de-B}` → 403.
   - `PATCH /api/profile/me` con `userId`/`usuarioId` de B → 400; el perfil de B no cambia.
2. Crear dos empresas. Como Editor de E2 pedir `GET /api/company/courses/{id-de-curso-de-E1}` → 403, igual que un id inventado.
3. En una misma empresa, crear dos proyectos; tomar el id de un entregable del primero y llamar `PUT /api/company/courses/{id-del-segundo}/deliverables/{id-del-primero}` → 403.
4. Contraprueba: el Editor de E1 ve y edita sus cursos; A edita su perfil en `/perfil`.

Inscripción y progreso direccionable siguen pendientes. Para RF6, crear/publicar una oferta de E1, postular un Talento con CV y solicitar su listado/perfil/CV desde E2: se deniega igual que para UUID inexistente. Ver el flujo completo en [RF6, primera etapa](../sprint2/rf6-ofertas-postulaciones.md).
