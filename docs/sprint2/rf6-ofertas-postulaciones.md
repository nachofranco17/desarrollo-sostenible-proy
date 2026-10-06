# RF6 — Ofertas laborales y postulaciones, primera etapa

Fecha: 06/10/2026. Alcance: backend, persistencia, seguridad y pruebas. El frontend queda pendiente.

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

## Filtros del listado empresarial

Se pueden combinar `q` (nombre/apellido), `especializacion` (valor exacto) y `fechaDesde`/`fechaHasta` (fechas ISO `YYYY-MM-DD`). Los extremos de fecha son inclusivos y se interpretan en UTC: desde las 00:00 del primer día hasta antes de las 00:00 del día siguiente al último. Una fecha inicial posterior a la final es inválida.

Primero se aplica empresa + oferta en SQL parametrizado. Sobre esas filas autorizadas se aplican filtros; especialización se compara con pertenencia exacta de la lista JSON, sin `LIKE` sobre su texto. No hay filtros sobre campos sensibles ni sobre otras postulaciones. Esta etapa no agrega paginación.

## Persistencia y concurrencia

La migración nueva es `V8__ofertas_postulaciones_curriculums.sql`, posterior a V7. Crea ofertas, requisitos, versiones de CV y postulaciones, además de la referencia al CV actual en el perfil del Talento.

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
backend/src/main/resources/db/migration/V8__ofertas_postulaciones_curriculums.sql
backend/src/test/java/uy/edu/um/xperience/OfferIntegrationTest.java
backend/src/test/java/uy/edu/um/xperience/CurriculumIntegrationTest.java
backend/src/test/java/uy/edu/um/xperience/ApplicationIntegrationTest.java
backend/src/test/java/uy/edu/um/xperience/Rf6DatabaseConstraintsTest.java
backend/src/test/java/uy/edu/um/xperience/Rf6MigrationTest.java
docs/sprint2/rf6-ofertas-postulaciones.md
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

El frontend, cierre/reapertura/eliminación, transiciones de postulación, RF4 (inscripciones, progreso, proyectos completados y logros) y RS19 (protección del almacén de logs) quedan pendientes. No se incluyen datos de formación inexistentes ni endpoints fuera del alcance cerrado.

Ejecutar las pruebas específicas de RF6 y luego, desde `backend`:

```powershell
.\mvnw.cmd test
```

Desde la raíz, verificar `git diff --check`. No se requiere commit ni push para estas verificaciones.

Validación ejecutada: 80 pruebas específicas aprobadas y regresión completa de 211 pruebas, con cero fallos, errores u omisiones. También pasó `git diff --check`, incluidos los archivos nuevos. Se usó el Maven Wrapper de Windows con una caché local, sin modificar dependencias ni configuración del proyecto.

Las pruebas usan H2 en modo PostgreSQL, como la suite existente. No se ejecutó una validación adicional contra una instancia real de PostgreSQL.
