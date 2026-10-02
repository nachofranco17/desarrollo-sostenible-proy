# RS11 — Autorización por instancia de recurso

Requerimiento de seguridad: **RS11** (ASVS v5.0.0-8.2.2, documentado como R11 en el modelo)
Versión: 1.0

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

`INSCRIPTO` y `POSTULANTE` siguen denegados en el evaluador hasta que existan esas relaciones reales (igual que en [permisos-minimos.md](../permisos-minimos.md)).

## Recursos cubiertos (funcionalidad existente)

| Recurso | Relación | Dónde se aplica |
|---|---|---|
| Perfil del Talento | `PROPIO` | `ProfileService`: carga por `sujeto.usuarioId`; `autorizar(..., perfil.gestionar, profile)` |
| Cuenta | `PROPIO` | `ProtectedAccounts` + `filtrar` (ya existente) |
| Membresía / staff | `ORG` | `StaffInvitationService` / `StaffRoleService` + `filtrar` (ya existente) |
| Curso / proyecto | `ORG` | `CourseService.owned`: `find(id, sujeto.empresaId)` + `autorizar` |
| Lección, entregable, material | Heredan el curso/proyecto | Consultas con `curso_id` + `empresa_id` de la sesión; id suelto no alcanza |

El evaluador reconoce entidades `TalentProfile` y `Course` al mapear atributos de instancia.

## Endpoints tocados

- `GET/PATCH /api/profile/me` — identidad solo desde la sesión/`Sujeto`; sin rutas `/profile/{id}`.
- Todas las rutas de `/api/company/courses/**` — empresa solo desde el sujeto; nested resources exigen coincidencia de curso + empresa.

No se agregaron endpoints nuevos de inscripción, progreso, oferta ni postulación.

## Acceso horizontal

- Talento A no puede leer/escribir el perfil de B por URL (`/profile/{idB}`) ni inyectando ids en el body (campos desconocidos → 400; la identidad no cambia).
- Staff de la empresa B que conoce el UUID de un curso/entregable de la empresa A recibe 403 idéntico al de un UUID inexistente.
- Conocer el UUID de un entregable no permite operarlo bajo otro proyecto (`/courses/{proyectoB}/deliverables/{entregableDeA}` → 403).

## Camino indirecto

Hoy no existe API de inscripciones ni listado de inscriptos anidado bajo proyecto. El camino indirecto que **sí** existe y queda cubierto es el de recursos anidados de RF5:

Proyecto autorizado → entregable / lección / material de **otro** proyecto (mismo o distinto UUID de curso en la ruta).

Cada instancia anidada se busca con `curso_id` de la ruta **y** `empresa_id` de la sesión. Acceder al proyecto padre no autoriza instancias hijas ajenas a ese padre.

Cuando existan inscripciones, el mismo patrón del modelo (`filtrar` / consulta con dueño) deberá aplicarse a `/inscripcion/{id}` y a cualquier listado anidado bajo proyecto; no se implementó ese módulo aquí.

## Recursos no cubiertos por falta de funcionalidad

| Recurso | Motivo |
|---|---|
| Inscripción | Sin tablas ni endpoints de negocio |
| Progreso (como recurso direccionable) | Solo campos vacíos derivados en el perfil; sin API propia |
| Postulación | Sin tablas ni endpoints |
| Oferta | Sin tablas ni endpoints de negocio |
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
7. Acciones `inscripcion.ver` / `postulacion.ver` / etc. no autorizan instancias ajenas a nivel de política (pendiente de API).

También siguen vigentes las pruebas de aislamiento en `ProfileIntegrationTest` y `CourseIntegrationTest`.

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

Inscripción, progreso direccionable y postulación de B no se pueden ejercitar todavía: esas APIs no existen.
