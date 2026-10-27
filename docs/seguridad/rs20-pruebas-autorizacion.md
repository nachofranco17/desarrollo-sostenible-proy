# RS20 — Pruebas automatizadas de autorización

Fecha: 15/10/2026. Referencia: [SEG-R1](matriz-control-acceso.md) y su [traducción a acciones y alcances](modelo-autorizacion.md).

## Cobertura de la matriz

`AuthorizationMatrixTest` lee la tabla del modelo como oráculo independiente del evaluador y de la tabla de permisos. Además contrasta sus celdas con las 24 filas de SEG-R1 y los cinco consumidores. Las operaciones agrupadas de SEG-R1 se separan en acciones; se agregan las tres acciones de sesión documentadas en el modelo.

| Comprobación | Ejecuciones | Caso de prueba |
|---|---:|---|
| Permiso concedido, recurso y relación válidos | 54 | `everyGrantedCombinationHasAPositiveCase` |
| Mismo permiso, recurso fuera de alcance y recurso ausente | 54 | `everyGrantedCombinationHasANegativeCase` |
| Rol sin permiso, incluso con atributos favorables del recurso | 91 | `everyForbiddenRoleActionIsDeniedEvenWithFavorableResourceAttributes` |
| SEG-R1 coincide con la traducción, sin filas o consumidores omitidos | 1 | `actionTranslationCoversEveryR1RowAndEveryConsumerWithoutChangingItsMeaning` |
| Inscripción real; acceso tras baja; revocación y banderas falsas | 1 | `enrollmentIsPersistedAndSurvivesWithdrawalButCannotBeForged` |
| Cambio de rol y baja invalidan el sujeto anterior | 1 | `membershipChangesInvalidateThePreviouslyResolvedSubjectImmediately` |
| **Total** | **202** | **145 combinaciones de rol y acción** |

Cada celda con permiso tiene un positivo y un negativo de instancia. Una celda denegada se comprueba como denegada; el acceso positivo al mismo recurso se prueba con los consumidores que sí tienen permiso. No se inventa una excepción positiva para un rol al que SEG-R1 prohíbe siempre esa acción.

Los alcances negativos incluyen otro dueño, otra empresa, contenido sin publicar, curso/proyecto sin inscripción y una postulación no persistida. `POSTULANTE` usa postulación y CV reales; `INSCRIPTO` usa cursos y proyectos con inscripciones reales. Las operaciones sensibles concedidas incluyen una prueba de reautenticación vigente en la sesión.

### Correspondencia por acción y consumidor

En cada celda distinta de — se ejecutan los dos métodos de permiso concedido; en cada — se ejecuta el método de rol sin permiso.

| Acción | Operación | Visitante | Talento | Admin | Reclutador | Editor |
|---|---|---|---|---|---|---|
| `cuenta.registrar` | Registrarse como Talento | GLOBAL | — | — | — | — |
| `sesion.iniciar` | Iniciar sesión | GLOBAL | — | — | — | — |
| `sesion.cerrar` | Cerrar la propia sesión | — | PROPIO | PROPIO | PROPIO | PROPIO |
| `sesion.reautenticar` | Confirmar la identidad antes de una operación sensible (R23) | — | — | PROPIO | — | — |
| `cuenta.gestionar` | Ver, modificar configuración y eliminar la propia cuenta | — | PROPIO | PROPIO | PROPIO | PROPIO |
| `perfil.gestionar` | Ver y modificar el propio perfil | — | PROPIO | — | — | — |
| `perfil.ver_postulante` | Ver el perfil de un postulante | — | — | POSTULANTE | POSTULANTE | — |
| `catalogo.ver` | Buscar y ver cursos/proyectos publicados | PUBLICADO | PUBLICADO | PUBLICADO | PUBLICADO | PUBLICADO |
| `curso.gestionar` | Crear, modificar, cambiar estado y eliminar cursos/proyectos, lecciones y entregables | — | — | ORG | — | ORG |
| `curso.ver_borrador` | Ver borradores | — | — | ORG | — | ORG |
| `material.subir` | Subir o reemplazar material | — | — | ORG | — | ORG |
| `material.descargar` | Descargar material | — | INSCRIPTO | ORG | — | ORG |
| `inscripcion.crear` | Inscribirse a un curso/proyecto | — | PUBLICADO | — | — | — |
| `inscripcion.ver` | Ver su inscripción y avance | — | PROPIO | — | — | — |
| `leccion.completar` | Marcar una lección como completada | — | INSCRIPTO | — | — | — |
| `entregable.responder` | Enviar respuesta a un entregable | — | INSCRIPTO | — | — | — |
| `inscripcion.ver_inscriptos` | Ver inscriptos y su avance | — | — | ORG | — | ORG |
| `oferta.ver` | Ver ofertas publicadas | — | PUBLICADO | PUBLICADO | PUBLICADO | PUBLICADO |
| `oferta.gestionar` | Crear, modificar, cerrar y ver borradores | — | — | ORG | ORG | — |
| `postulacion.crear` | Postularse a una oferta | — | PUBLICADO | — | — | — |
| `postulacion.ver` | Ver sus postulaciones y su estado | — | PROPIO | — | — | — |
| `postulacion.ver_listado` | Ver el listado de postulantes | — | — | ORG | ORG | — |
| `postulacion.cambiar_estado` | Cambiar el estado de una postulación | — | — | ORG | ORG | — |
| `curriculum.subir` | Subir o reemplazar el currículum | — | PROPIO | — | — | — |
| `curriculum.descargar` | Descargar un currículum | — | PROPIO | POSTULANTE | POSTULANTE | — |
| `staff.invitar` | Invitar un usuario al staff | — | — | ORG | — | — |
| `staff.cambiar_rol` | Cambiar el rol de un miembro | — | — | ORG | — | — |
| `staff.dar_baja` | Dar de baja a un miembro | — | — | ORG | — | — |
| `staff.ver` | Ver miembros y su rol | — | — | ORG | — | — |

## Pruebas HTTP complementarias

| Recurso o condición | Suite |
|---|---|
| Cuenta, sesión, credenciales, CSRF y cierre de sesión | `AuthIntegrationTest`, `PolicyContractTest` |
| Perfil propio, campos protegidos y recursos ajenos | `ProfileIntegrationTest`, `Rs11InstanceAuthorizationTest` |
| Catálogo, borradores, cursos, proyectos y empresa | `CatalogIntegrationTest`, `CourseIntegrationTest` |
| Inscripción propia y modificación del avance | `EnrollmentIntegrationTest` |
| Material de un Talento inscripto; otro Talento y recurso anidado rechazados; baja del curso | `CourseIntegrationTest.enrolledTalentDownloadsMaterialIncludingAfterWithdrawalButCannotSwitchCourseOrUser` |
| Ofertas publicadas y borradores; creación empresarial y requisitos | `OfferIntegrationTest` |
| CV privado y versionado; autenticación y campos estrictos | `CurriculumIntegrationTest` |
| Postulaciones propias, listado, filtros, perfil y CV; otra empresa rechazada | `ApplicationIntegrationTest` |
| Invitación, asignación, cambio de rol, baja y reautenticación | `StaffInvitationIntegrationTest`, `PolicyContractTest` |
| Denegar por defecto, errores del evaluador y convención de endpoints | `PolicyContractTest`, `EndpointConventionTest`, `LeastPrivilegeTest` |
| Registro de accesos denegados, incluso si falla el logger | `Rs18DeniedAccessLogTest`, `Rs18LoggerFailureTest` |

`invitacion.aceptar` es la excepción de SEG-R1 basada en un token, sin rol autenticado; se prueba en la suite de invitaciones. El alta de empresas continúa siendo un script interno. El acceso a los registros de seguridad no se expone por la API.

Los permisos de `leccion.completar`, `entregable.responder` y `postulacion.cambiar_estado` se prueban en el evaluador. Sus endpoints de negocio no se incorporan en esta entrega. La cobertura de política no afirma que esas funcionalidades estén implementadas.

## Ejecución y pipeline

Desde `backend`, en PowerShell:

```powershell
.\mvnw.cmd "-Dtest=AuthorizationMatrixTest,CourseIntegrationTest" test
.\mvnw.cmd clean verify
```

`.github/workflows/validacion.yml` ejecuta la regresión del backend con JDK 21 y la instalación, auditoría y compilación del frontend con Node 22. Se dispara por push, pull request y ejecución manual. Un código de salida distinto de cero en Maven hace fallar el job. Los reportes XML/TXT se conservan como artefactos aun cuando falla la suite. No hay `continue-on-error` en las pruebas.

## Fallo intencional para verificar RS20.3

Primero debe pasar el pipeline de la versión correcta. Luego crear una rama `experimento/rs20-...` desde ese commit y ejecutar `scripts/introducir-fallo-rs20.ps1`. El script exige una rama de prueba limpia y elimina una sola comparación: la igualdad entre la empresa de la sesión y la del recurso en el alcance ORG. No modifica migraciones ni pruebas, y no hace commit ni push.

Ejecutar nuevamente `AuthorizationMatrixTest`: debe fallar por aserciones de accesos entre empresas. Un error de compilación, descarga, infraestructura o inicialización no acredita esta comprobación. Conservar el resumen y los nombres de los casos que detectan el fallo.

Publicar ese commit únicamente en la rama del experimento. El mismo workflow debe mostrar el job de autorización en rojo y reportes con esos fallos. Registrar la URL de esa ejecución y la del pipeline correcto. Después revertir el commit del fallo en la rama del experimento y comprobar que el pipeline vuelva a verde; regresar a `revision-rf6-rs20`.

## Evidencia local disponible

- Antes de corregir INSCRIPTO: 202 pruebas, 4 fallos de aserción, 0 errores, 0 omitidas. Detectó los tres permisos de inscripción y la conservación del acceso tras la baja.
- Después de corregir la inscripción persistida y la descarga: 220 pruebas de matriz y cursos, 0 fallos, 0 errores, 0 omitidas.
- Regresión completa de la versión correcta, ejecutada en Windows con JDK 21: **432 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
- Experimento local sobre `AuthorizationMatrixTest`: **202 pruebas, 20 fallos de aserción, 0 errores, 0 omitidas**. Los casos fuera del alcance ORG detectaron que se admitía un recurso de otra empresa.

## Evidencia de GitHub Actions — 15/10/2026

| Estado verificado | Commit | Resultado | Ejecución |
|---|---|---|---|
| Código correcto en `revision-rf6-rs20` | `b3b1cd4` | Backend y frontend aprobados | [Pipeline correcto](https://github.com/nachofranco17/desarrollo-sostenible-proy/actions/runs/38818022271) |
| Fallo intencional, únicamente en `experimento/rs20-aislamiento` | `3813e8b` | Backend rechazado: **432 pruebas, 24 fallos, 0 errores, 0 omitidas; BUILD FAILURE; salida 1**. Frontend aprobado. | [Pipeline que detectó el fallo](https://github.com/nachofranco17/desarrollo-sostenible-proy/actions/runs/38818813717) |
| Reversión del fallo en la rama del experimento | `5bff3ef` | Backend y frontend nuevamente aprobados | [Pipeline restaurado](https://github.com/nachofranco17/desarrollo-sostenible-proy/actions/runs/38819572962) |

De los 24 fallos en CI, 20 pertenecen a los negativos de instancia de `AuthorizationMatrixTest`; los otros cuatro son casos de `AuthIntegrationTest`, `PolicyContractTest` y `StaffInvitationIntegrationTest`. Todos son fallos de aserción por acceso admitido cuando debía denegarse, sin errores de inicialización o compilación.

La secuencia código correcto → defecto de autorización detectado → reversión aprobada acredita RS20.3. El commit defectuoso no se integró en `revision-rf6-rs20`; se conserva en el historial de la rama del experimento junto con su reversión. Las URLs anteriores conservan la referencia de las ejecuciones aunque los artefactos de pruebas caduquen según su retención.
