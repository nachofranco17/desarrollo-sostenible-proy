# RF6 — Ofertas laborales y postulaciones

Actualizado: 15/10/2026. Alcance: interfaz web, backend, persistencia, seguridad y pruebas. Se conserva el alcance acordado de crear y consultar postulaciones, con gestión de CV y requisitos publicados de cualquier empresa.

## Comportamiento

ADMIN y RECLUTADOR crean ofertas `BORRADOR` para su empresa. Solo editan título, descripción y `cursosRequeridos` mientras la oferta sea borrador. Los UUID referencian cursos/proyectos publicados del catálogo, incluso de otras empresas; una lista vacía es válida. Cada referencia se valida al crear, editar y publicar. Una oferta válida pasa a `PUBLICADA` y ya no se puede editar. No hay cierre, reapertura ni eliminación.

TALENTO y el staff con rol ADMIN, RECLUTADOR o EDITOR pueden listar y consultar ofertas publicadas. El visitante no puede acceder a las ofertas, aunque el catálogo RF3 sigue público. No cambian los endpoints ni DTOs de RF3.

El Talento sube/reemplaza su CV actual con un PDF de hasta 5 MiB. Se valida el tipo, la firma PDF, el tamaño y el nombre seguro. El archivo se guarda mediante `FileStorage` privado; ninguna respuesta contiene su ubicación. Cada reemplazo crea una versión: las postulaciones conservan la referencia inmutable a la versión original.

Para postularse hace falta un CV actual. El POST no admite campos de negocio: oferta desde la ruta, Talento desde la sesión, CV desde la referencia actual y estado/fecha desde servidor. Se registra una única postulación `ENVIADA` por Talento y oferta. No hay transición ni edición de postulaciones en esta etapa.

ADMIN y RECLUTADOR consultan postulantes solo de ofertas de su empresa. El listado incluye `nombre`, `apellido`, `correo` y `especializaciones`; no contiene teléfono, preferencias, otros procesos, roles, permisos ni secretos. El perfil contextual y la descarga empresarial se acceden mediante la postulación concreta. El CV descargado es la versión adjunta, aunque el Talento haya reemplazado después el actual.

## Endpoints y acciones existentes

| Método | Ruta | Acción |
|---|---|---|
| POST | `/api/company/offers` | `oferta.gestionar` |
| GET | `/api/company/offers` | `oferta.gestionar` |
| GET | `/api/company/offers/{id}` | `oferta.gestionar` |
| PUT | `/api/company/offers/{id}` | `oferta.gestionar` |
| POST | `/api/company/offers/{id}/publish` | `oferta.gestionar` |
| GET | `/api/offers` | `oferta.ver` |
| GET | `/api/offers/{id}` | `oferta.ver` |
| POST | `/api/profile/me/curriculum` | `curriculum.subir` |
| GET | `/api/profile/me/curriculum` | `curriculum.descargar` |
| GET | `/api/profile/me/curriculum/metadata` | `curriculum.descargar` |
| POST | `/api/offers/{id}/applications` | `postulacion.crear` |
| GET | `/api/applications/me` | `postulacion.ver` |
| GET | `/api/company/offers/{id}/applications` | `postulacion.ver_listado` |
| GET | `/api/company/offers/{id}/applications/{applicationId}/profile` | `perfil.ver_postulante` |
| GET | `/api/company/offers/{id}/applications/{applicationId}/curriculum` | `curriculum.descargar` |

No se agregan permisos ni se modifican las migraciones de permisos existentes. Se mantienen los 54 permisos de V4. Todos los endpoints declaran `@RequiereAccion` y siguen el PEP existente.

## Contratos de entrada y salida

- Oferta: `titulo`, `descripcion`, `cursosRequeridos` (lista de UUID). El servidor obtiene empresa, creador, ids, fechas y estados. Publicar admite cuerpo vacío o `{}`.
- CV: multipart con la parte `archivo`; ningún id/dueño/estado/ruta proviene del cliente. Los metadatos seguros son id, nombre original seguro, tipo MIME, tamaño y fecha.
- Postulación: cuerpo vacío o `{}`. Se rechaza cualquier campo, incluidos `usuarioId`, `talentoId`, `empresaId`, `estado`, `fecha` y `curriculumId`.
- Consulta propia: id, oferta, título, nombre de empresa, estado y fecha.
- Listado empresarial: id de postulación, estado, fecha y proyección segura del perfil. El perfil contextual contiene solo los cuatro campos admitidos.

Los DTOs y parsers rechazan campos desconocidos/prohibidos antes de aplicar cambios. `AuthorizationService.camposLegibles`/`camposEscribibles` centraliza los campos RF6. No se serializan modelos internos con claves de almacenamiento o ids de dueño.

La consulta `/profile/me/curriculum/metadata` devuelve esos cinco metadatos del CV actual del Talento autenticado; no admite parámetros de dueño ni otros filtros. Responde 404 si aún no cargó un CV, 401 sin sesión y 403 para staff. Autoriza la instancia persistida con `curriculum.descargar` y usa `Cache-Control: no-store`. La descarga PDF conserva su ruta original.

## Interfaz web

| Consumidor | Pantalla | Ruta |
|---|---|---|
| Cuenta habilitada | Ofertas publicadas y sus requisitos | `/ofertas`, `/ofertas/:id` |
| Talento | Cargar, reemplazar y descargar su CV | `/mi-curriculum` |
| Talento | Consultar sus postulaciones | `/mis-postulaciones` |
| Administrador o Reclutador | Ofertas propias, borrador y publicación | `/gestion-ofertas`, `/gestion-ofertas/nueva`, `/gestion-ofertas/:id` |
| Administrador o Reclutador | Postulantes con filtros | `/gestion-ofertas/:id/postulantes` |
| Administrador o Reclutador | Perfil y CV de la postulación | `/gestion-ofertas/:id/postulantes/:applicationId/perfil` |

Los accesos aparecen en **Mi cuenta** según el rol. El selector de requisitos busca en el catálogo publicado y conserva la selección al cambiar la búsqueda. El Talento ve el nombre de su CV actual antes de postularse; si ya se postuló, ve la confirmación y el enlace a sus postulaciones. Los filtros del listado se conservan al regresar del perfil del postulante.

Las comprobaciones de ruta solo deciden la presentación. El backend exige permiso y relación con el recurso en cada solicitud, incluso para descargas. Al cambiar recurso, usuario, empresa o rol se descartan los resultados anteriores; una respuesta tardía de una solicitud reemplazada no se muestra. Un 401/403 limpia los datos afectados y vuelve a consultar la sesión.

## Recorrido manual desde la interfaz

1. Con Administrador o Reclutador, entrar en **Mi cuenta → Gestionar ofertas laborales → Crear oferta laboral**. Completar título y descripción; guardar el borrador.
2. Elegir los cursos o proyectos requeridos del catálogo. Pueden pertenecer a cualquier empresa, pero deben estar publicados. Guardar y presionar **Publicar oferta**.
3. En otra sesión de navegador, ingresar como Talento. Cargar un PDF de hasta 5 MiB desde **Mi currículum**.
4. Entrar en **Ver ofertas laborales**, abrir la oferta, revisar requisitos y el nombre del CV, y presionar **Postularme**.
5. Comprobar el mensaje de registro y la fila **Enviada** en **Mis postulaciones**. Al volver a la oferta, debe indicar que ya existe la postulación.
6. Volver a la sesión empresarial y abrir **Ver postulantes** en esa oferta.
7. Consultar el perfil y descargar el CV de la postulación. Si el Talento reemplaza después su CV actual, esta descarga debe conservar la versión que adjuntó.
8. Combinar filtros de nombre/apellido, especialización y fechas; probar una búsqueda sin coincidencias y **Limpiar filtros**. Las fechas se interpretan en UTC, como el contrato de la API.
9. Con una segunda empresa, abrir directamente las mismas rutas de postulantes y perfil: debe mostrarse la denegación, sin datos del Talento. Las solicitudes directas de listado, perfil y CV deben devolver 403.

Para repetir desde la primera prueba, usar otra oferta o Talento: no se elimina ni modifica una postulación ya registrada.

## Filtros del listado empresarial

Se pueden combinar `q` (nombre/apellido), `especializacion` (valor exacto) y `fechaDesde`/`fechaHasta` (fechas ISO `YYYY-MM-DD`). Los extremos de fecha son inclusivos y se interpretan en UTC: desde las 00:00 del primer día hasta antes de las 00:00 del día siguiente al último. Una fecha inicial posterior a la final es inválida.

Primero se aplica empresa + oferta en SQL parametrizado. Sobre esas filas autorizadas se aplican filtros; especialización se compara con pertenencia exacta de la lista JSON, sin `LIKE` sobre su texto. No hay filtros sobre campos sensibles ni sobre otras postulaciones. Esta etapa no agrega paginación.

## Persistencia y concurrencia

La migración nueva es `V9__ofertas_postulaciones_curriculums.sql`, posterior a V8 (inscripciones). Crea ofertas, requisitos, versiones de CV y postulaciones, además de la referencia al CV actual en el perfil del Talento.

- Oferta conserva `empresa_id` y creador del servidor, con estados limitados a BORRADOR/PUBLICADA.
- Requisito tiene clave compuesta para impedir duplicados y FK a un curso/proyecto existente. No impone la empresa del curso como empresa de la oferta.
- CV tiene dueño, metadatos y clave privada; la versión anterior se conserva cuando está adjunta a una postulación.
- Postulación exige `UNIQUE(oferta_id, talento_id)`, FK compuesta oferta/empresa, FK compuesta CV/Talento y estado único ENVIADA. Su Talento debe tener perfil.
- La referencia al CV actual debe pertenecer al mismo Talento; no se acepta un CV de otra cuenta.

Los servicios son transaccionales y las mutaciones bloquean las filas relevantes. Publicación y postulación validan el estado bajo bloqueo; el reemplazo del CV y la postulación coordinan la referencia actual. La restricción única resuelve también envíos simultáneos.

## Controles de seguridad

RS11: consultas propias o empresariales desde el sujeto y autorización de cada instancia. La ruta anidada fija oferta + empresa + postulación. Recurso ajeno e inexistente generan la misma denegación.

RS12: entradas estrictas y proyecciones de salida explícitas. Como solo existe ENVIADA, el teléfono nunca se devuelve. No se devuelven preferencias, otras postulaciones, secretos ni claves/rutas físicas.

RS15: `empresa_id` siempre deriva de la sesión. La FK compuesta evita relaciones cruzadas en la base. El listado aplica el alcance empresarial antes de los filtros.

`POSTULANTE` se verifica consultando la postulación persistida mediante `ApplicantAccess`: deben coincidir postulación, oferta, empresa, Talento y versión adjunta de CV. Un UUID aislado, flag, `ResourceAccess` o registro inventado no habilita acceso.

Los módulos JDBC cumplen el contrato de `filtrar` mediante consultas acotadas + `autorizar`, sin Specifications sobre records. Los permisos y la vigencia de la cuenta/membresía siguen consultándose en cada solicitud.

RS18: las denegaciones usan `Denegaciones` y `RegistroAccesosEstructurado`. Se registran rol/campo/empresa/instancia/archivo según corresponde, sin cuerpos, perfiles, nombres de archivo, bytes, filtros ni ubicaciones. La falla del logger mantiene la denegación. CSRF y cookies de sesión siguen activos.

## Archivos de esta etapa

Nuevos bajo `backend/src/main/java/uy/edu/um/xperience/`:

```text
offer/Offer.java
offer/OfferInput.java
offer/OfferQuery.java
offer/OfferView.java
offer/OfferRepository.java
offer/OfferService.java
offer/CompanyOfferController.java
offer/PublishedOfferController.java
curriculum/Curriculum.java
curriculum/CurriculumInput.java
curriculum/CurriculumView.java
curriculum/CurriculumRepository.java
curriculum/CurriculumService.java
curriculum/CurriculumController.java
application/JobApplication.java
application/ApplicationInput.java
application/ApplicationCriteria.java
application/ApplicationQuery.java
application/ApplicationView.java
application/ApplicationRepository.java
application/ApplicationService.java
application/TalentApplicationController.java
application/CompanyApplicationController.java
profile/ApplicantProfileView.java
security/ApplicantAccess.java
```

Otros nuevos:

```text
backend/src/main/resources/db/migration/V9__ofertas_postulaciones_curriculums.sql
backend/src/test/java/uy/edu/um/xperience/OfferIntegrationTest.java
backend/src/test/java/uy/edu/um/xperience/CurriculumIntegrationTest.java
backend/src/test/java/uy/edu/um/xperience/ApplicationIntegrationTest.java
backend/src/test/java/uy/edu/um/xperience/Rf6DatabaseConstraintsTest.java
backend/src/test/java/uy/edu/um/xperience/Rf6MigrationTest.java
docs/sprint2/rf6-ofertas-postulaciones.md
frontend/src/auth.tsx
frontend/src/ofertas.tsx
frontend/e2e/rf6.spec.ts
```

Modificados:

```text
README.md
backend/src/main/java/uy/edu/um/xperience/security/AuthorizationService.java
backend/src/main/java/uy/edu/um/xperience/security/Denegaciones.java
backend/src/main/java/uy/edu/um/xperience/security/RegistroAccesosEstructurado.java
backend/src/test/java/uy/edu/um/xperience/PolicyContractTest.java
backend/src/test/java/uy/edu/um/xperience/Rs11InstanceAuthorizationTest.java
backend/src/test/java/uy/edu/um/xperience/Rs18DeniedAccessLogTest.java
docs/permisos-minimos.md
docs/seguridad/modelo-autorizacion.md
docs/seguridad/restricciones-campo.md
docs/seguridad/rs11-autorizacion-por-instancia.md
docs/seguridad/rs18-registro-accesos-denegados.md
```

## Pruebas y pendientes

La cobertura comprueba gestión ADMIN/RECLUTADOR, denegación EDITOR/TALENTO, requisitos publicados, borradores ocultos, PDF seguro, postulación con CV original, duplicados, campos manipulados, filtros combinados, aislamiento empresarial, acceso propio, ausencia de campos sensibles, autenticación/CSRF, eventos RS18 y restricciones de base de datos. `PolicyContractTest` valida relaciones persistidas e impide contextos manipulados.

El cierre/reapertura/eliminación de ofertas, transiciones de postulación, completado de lecciones, respuestas a entregables y logros de RF4 y RS19 (protección del almacén de logs) quedan fuera de esta entrega. No se incluyen datos de formación inexistentes ni endpoints fuera del alcance cerrado.

Ejecutar las pruebas específicas de RF6 y luego, desde `backend`:

```powershell
.\mvnw.cmd test
```

Desde la raíz, verificar `git diff --check`. No se requiere commit ni push para estas verificaciones.

La validación inicial del backend (06/10/2026) aprobó 80 pruebas específicas y una regresión de 211 pruebas. Tras la integración con Sprint 2 y RS20, la regresión de Windows pasó a 432 pruebas aprobadas; sus evidencias están en [RS20](../seguridad/rs20-pruebas-autorizacion.md).

La incorporación de la interfaz agrega `CurriculumIntegrationTest.metadataIsPrivateCurrentAndRejectsOwnerOverrides`: prueba CV actual/ausente, metadatos seguros, sustitución del CV, denegación a visitante/staff y rechazo de dueño por parámetros. Pasaron las 21 pruebas de CV y las dos de convención de endpoints; también la compilación TypeScript/Vite de la interfaz.

La prueba de navegador `frontend/e2e/rf6.spec.ts` prepara dos empresas, un curso y un proyecto publicados y dos Talentos. Realiza publicación, requisitos, carga y reemplazo de CV, postulación y consulta desde las pantallas reales; verifica filtros combinados, descarga de la versión original, rechazo de duplicados, aislamiento empresarial y presentación móvil. Solo la preparación de contenidos/perfiles y las comprobaciones negativas directas usan la API. No sustituye el backend por respuestas simuladas.

Con el JAR actualizado y el backend dev abierto, ejecutar desde `frontend` en Windows:

```powershell
npm.cmd run build
npm.cmd run test:e2e -- rf6.spec.ts --workers=1
```

Validación final en Windows, 15/10/2026: compilación frontend aprobada; backend con 433 pruebas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS. Recorrido real RF6 con Playwright: 1 passed (40.3s).

Las pruebas usan H2 en modo PostgreSQL, como la suite existente. No se ejecutó una validación adicional contra una instancia real de PostgreSQL.
