# Dependency-Check — Sprint 2

## Evidencia inicial

Fecha: 15/10/2026. Commit analizado: `0b2527a`. Motor: OWASP Dependency-Check **13.0.0**. NVD consultada a las 20:23:52 de Uruguay; reporte generado a las 20:25:22. Se entregaron HTML y JSON; el proceso terminó con salida 0.

El JSON original registra **2.036 componentes, 71 componentes con alertas y 159 identificadores de vulnerabilidad distintos**. Es un inventario amplio del disco del proyecto: incluye una caché histórica de Maven bajo `.tools/maven-repository`. El código 0 acredita finalización, no ausencia de vulnerabilidades.

| Ubicación | Componentes señalados | Interpretación |
|---|---:|---|
| Solo herramientas/caché de Maven | 65 | Versiones descargadas que no aparecen en el JAR actual. Conservar como hallazgos de herramientas; no tratarlos como falsos positivos ni afirmar que estén corregidos. |
| JAR `backend/target/xperience.jar` | 6 | Bibliotecas de la aplicación; revisar versiones, componente afectado y condiciones de explotación. |

Las seis entradas del JAR reúnen **81 identificadores distintos**. Hay 18 asociaciones clasificadas CRITICAL, 28 HIGH, 31 MEDIUM y 4 LOW por este reporte. Estas severidades automáticas pueden diferir de la valoración del proveedor y no acreditan por sí solas una ruta explotable en la aplicación. Los archivos relacionados se tuvieron en cuenta al distinguir caché y aplicación.

## Bibliotecas de la aplicación señaladas

| Biblioteca inicial | Versión | Alertas del JSON | Tratamiento |
|---|---|---:|---|
| Tomcat embebido | 10.1.52 | 34 | Actualizar la familia Tomcat a 10.1.61 mediante el BOM de Boot y verificar la nueva resolución. |
| Jackson Databind | 2.19.4 | 4 | Actualizar el BOM completo de Jackson a 2.21.7; conservar coherencia de core, annotations, databind y módulos. |
| Log4j API | 2.24.3 | 3 | Actualizar el BOM Log4j a 2.26.1. Parte de las alertas se refiere a Log4j Core o al puente de Log4j 1, que no se identifican en el JAR de esta aplicación. |
| Spring Framework, agrupado en spring-core | 6.2.16 | 38 | Boot 3.5.16 incorpora Framework 6.2.19. Persisten avisos posteriores para esta rama; requieren evaluación y plan de migración. |
| Spring Data JPA | 3.5.9 | 1 | Revisar CVE-2026-47834 y la versión resuelta tras actualizar Boot. No se identifican consultas nativas con Sort/Pageable derivados del usuario en el código revisado. |
| Spring Security, agrupado en spring-security-core | 6.5.8 | 1 | CVE-2018-1258 es un falso positivo de esta asociación: afecta a Framework 5.0.5, no a Framework 6.2.16. |

## Actualización compatible aplicada y validación

`backend/pom.xml` pasa de Spring Boot **3.5.11 a 3.5.16**, conserva Java 17 y fija los BOM/versiones de Tomcat **10.1.61**, Jackson **2.21.7** y Log4j **2.26.1**. La versión de Jackson actualiza toda la familia; no se reemplaza únicamente `jackson-databind`. No cambian las migraciones ni los contratos de negocio.

Las versiones se comprobaron en los metadatos públicos de Maven Central y en las bibliotecas del JAR identificadas en el informe final. Se ejecutaron `mvnw.cmd clean verify` y un nuevo escaneo. No se infiere un informe limpio a partir de los números de versión.

Validación de preparación (Linux, JDK 17, 15/10/2026): **433 pruebas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. El JAR generado contiene las versiones propuestas.

Validación en Windows, 15/10/2026 a las 20:47:14 de Uruguay: el usuario ejecutó `mvnw.cmd clean verify` con el parche de dependencias y obtuvo **BUILD SUCCESS**. El recorrido RF6 en navegador había aprobado antes de esta actualización (1 passed, 40.3 s); todavía no se ha repetido contra las nuevas dependencias.

**La rama Boot 3.5 ya terminó su soporte OSS.** El proveedor identifica 3.5.16 como la última versión pública y recomienda migrar a 4.0/4.1 para continuar recibiendo correcciones OSS. Por tanto, esta actualización reduce la antigüedad de las dependencias y permite actualizar Tomcat/Jackson/Log4j, pero no sustituye una migración ni corrige automáticamente todos los avisos de Spring. Ese trabajo debe quedar abierto; no se registra aceptación de riesgo por parte del equipo.

## Resultado del análisis final

Ejecución en Windows el **15/10/2026 a las 21:00:27 de Uruguay**, con duración de 75 segundos y salida 0. Carpeta: `dependency-check-sprint2-0b2527a-trabajo-20261015-205903`. Motor 13.0.0; NVD reutilizada tras su consulta de las 20:23:52. Se generaron HTML, JSON y contexto del análisis.

El proyecto corresponde a **`0b2527a` más cambios locales** en `backend/pom.xml`, este documento y el script de escaneo. No se atribuye el POM actualizado al commit base. El contexto registra los hashes del JAR, POM y lockfile. Los hashes de POM y lockfile coinciden con los archivos revisados al reconstruir sus finales de línea CRLF de Windows.

| Resultado automático | Valor |
|---|---:|
| Entradas de componentes analizadas | 1.654 |
| Componentes con alertas | 3 |
| Identificadores de vulnerabilidad distintos | 19 |
| CRITICAL / HIGH / MEDIUM / LOW del reporte | 6 / 7 / 5 / 1 |

| Biblioteca detectada | Versión final | Avisos finales | Evaluación |
|---|---|---:|---|
| Spring Framework, agrupado en spring-core | 6.2.19 | 17 | La versión sigue afectada; revisar condiciones por aviso y planificar actualización del stack. |
| Spring Data JPA | 3.5.13 | 1 | CVE-2026-47834; no se identificó en el código la combinación de consulta nativa y Sort/Pageable no confiable que requiere. |
| Spring Security, agrupado en spring-security-core | 6.5.11 | 1 | CVE-2018-1258: falso positivo confirmado frente al aviso oficial, que afecta a Framework 5.0.5. |
| Tomcat embebido | 10.1.61 | 0 identificados | Actualización reconocida en el inventario final. |
| Jackson Databind | 2.21.7 | 0 identificados | Actualización reconocida; BOM de la familia completo. |
| Log4j API | 2.26.1 | 0 identificados | Actualización reconocida en el inventario final. |

No se identificaron alertas del frontend en este resultado. Se contrastaron las **68 bibliotecas de `BOOT-INF/lib`** del JAR de preparación con las entradas y dependencias relacionadas del JSON de Windows: coinciden los **68 nombres y sus SHA-1**, sin faltantes ni versiones extra. Este cotejo confirma el inventario de esas bibliotecas; no prueba ausencia de vulnerabilidades ni restituye los metadatos que aportaría Central Analyzer.

El reporte inicial del JAR mostraba 81 identificadores y el final muestra 19. **No se presentan los 62 identificadores ausentes como 62 correcciones demostradas exclusivamente por el cambio de versión**: también cambió el alcance del directorio analizado y se omitió Central Analyzer. El informe amplio original conserva los hallazgos de la caché de herramientas.

Los 17 avisos de Framework se conservan individualmente en el resumen de evidencia enlazado debajo. La revisión de código no identificó uso de WebFlux, RSocket, SSE, vistas XSLT/FreeMarker, evaluación de expresiones SpEL del usuario, filtros UrlHandlerFilter ni UrlFilenameViewController. Tampoco se identificaron rutas de binding de propiedades sobre listas autoexpansibles ni renderizado HTML de errores de binding. Esto es una evaluación contextual del código, no una corrección de las versiones ni una supresión global. Para Content-Disposition se mantiene la revisión específica de los nombres de descarga y del conector Tomcat.

**Estado de Dependency-Check: análisis finalizado con alertas y limitaciones documentadas. No se declara un resultado libre de vulnerabilidades.** Quedan como trabajo separado la migración a versiones con soporte y parches públicos, la revisión contextual restante y completar la cobertura de servicios externos.

La evidencia compacta del repositorio está en [evidencias/dependency-check-sprint2-resumen.json](evidencias/dependency-check-sprint2-resumen.json): incluye los 19 identificadores con severidad y enlace oficial, inventario de las 68 bibliotecas, hashes de los archivos originales y contexto de ejecución. Los JSON y HTML completos se conservan en la carpeta de informes indicada; el resumen no los sustituye.

## Evaluación contextual y pendientes

- **CVE-2018-1258:** el aviso oficial limita la versión afectada a Framework 5.0.5 y la corrige en 5.0.6. La aplicación usa Framework 6.2.16 en el reporte inicial y 6.2.19 en el final. Se documenta el falso positivo sin agregar una supresión global.
- **CVE-2026-47834 (Data JPA):** requiere entrada Sort no confiable en una consulta nativa con Sort/Pageable. No se identificaron `@NativeQuery`, `nativeQuery=true` ni parámetros Sort/Pageable del usuario en `backend/src/main/java`. Esto describe el código revisado; no convierte una biblioteca afectada en una versión corregida.
- **Spring Framework:** varios avisos requieren WebFlux, RSocket, JMS, vistas XSLT/JSP/FreeMarker, SSE o evaluación de SpEL del usuario. El proyecto usa controladores REST MVC, React y un evaluador propio de permisos. Esa arquitectura es relevante para evaluar exposición, pero no se descartan en bloque los 38 avisos. La revisión restante y la migración siguen pendientes.
- **CVE-2026-59314 (Content-Disposition):** la aplicación sí construye encabezados de descarga. `CurriculumService.safeFileName` elimina caracteres ajenos a letras, números, espacio, punto, guion y guion bajo; `CourseService.cleanFileName` elimina controles y comillas. El proveedor distingue conectores que no rechazan caracteres maliciosos y la construcción de multipart saliente; el Tomcat usado es uno de los conectores que ya los rechaza. La sanitización y el conector son controles contextuales, no equivalen a actualizar Framework a una versión corregida.
- **Tomcat:** se actualizó a 10.1.61. El informe final reconoce esa versión y no le asocia alertas; los informes permanecen sujetos a su cobertura y a la base de datos consultada.

No hay supresiones nuevas ni se eliminan hallazgos para obtener artificialmente un resultado cero. Las pruebas funcionales de RF6 y RS20 no reemplazan el tratamiento de vulnerabilidades de dependencias.

## Escaneo reproducible de la aplicación

Después de compilar backend e instalar frontend, desde la raíz en PowerShell:

```powershell
.\scripts\dependency-check-sprint2.ps1 -Analizador 'RUTA\dependency-check.bat' -NvdApiKey 'CLAVE-NVD'
```

El script analiza el proyecto incluyendo el JAR completo, `frontend/node_modules` y sus dependencias de desarrollo. Excluye `.git`, datos privados `.data`, caché histórica `.tools`, caché Vite `.vite` y artefactos de pruebas. **Esas exclusiones definen el alcance de la aplicación entregada; no corrigen ni anulan los 65 hallazgos de herramientas del informe amplio.** Conservar ambos resultados para distinguir inventario de herramientas y artefacto de aplicación.

Los informes se guardan fuera del proyecto, en Descargas, con commit y fecha. Si hay cambios locales, la etiqueta incluye `-trabajo`. `contexto-analisis.json` registra estado Git y hashes del JAR, POM y lockfile; no contiene la clave NVD. Es preferible registrar un escaneo definitivo con el árbol limpio y el JAR correspondiente a ese commit.

Si Central Analyzer acumula demoras en consultas remotas, el script admite la opción explícita `-SinCentral`. Central aporta metadatos para identificar bibliotecas; omitirlo puede producir falsos negativos y no equivale a obtener la misma cobertura. La opción conserva habilitados Jar Analyzer, NVD, Node Audit y RetireJS, y registra `centralAnalyzerHabilitado: false` en el contexto. Si se utiliza, contrastar las bibliotecas y versiones reconocidas con el JAR y dejar esta limitación en el resultado. No se agrega una supresión de vulnerabilidades.

**La ejecución final utilizó `-SinCentral`**, tras observar consultas secuenciales de unos 30 segundos por JAR que demoraban el proceso. El contexto confirma Central deshabilitado. En el log aportado finalizaron NVD, Node Audit y RetireJS; **OSS Index no se ejecutó por falta de credenciales**. El resultado tiene esas limitaciones de cobertura. Para reproducir esa modalidad, agregar `-SinCentral` al comando anterior; por defecto el script conserva Central habilitado.

En el escaneo inicial finalizaron NVD, Node Audit y RetireJS. OSS Index quedó deshabilitado por falta de credenciales; no se obtuvo cobertura de ese servicio. El aviso de lockfile provenía de `frontend/.vite/deps/package.json`, una caché generada. Los módulos opcionales omitidos corresponden principalmente a otras plataformas; el `frontend/package-lock.json` real se conserva.

## Fuentes primarias

- [Opciones oficiales de Dependency-Check CLI](https://dependency-check.github.io/DependencyCheck/dependency-check-cli/arguments.html).
- [Función de Central Analyzer](https://dependency-check.github.io/DependencyCheck/analyzers/central-analyzer.html).
- [Última versión OSS de Boot 3.5 y recomendación de migración](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/).
- [CVE-2018-1258: versiones realmente afectadas](https://spring.io/security/cve-2018-1258/).
- [CVE-2026-47834: condiciones y versiones corregidas de Data JPA](https://spring.io/security/cve-2026-47834/).
- [CVE-2026-59313: condiciones y disponibilidad de parches Framework](https://spring.io/security/cve-2026-59313/).
- [CVE-2026-59282: condiciones de listas autoexpansibles en data binding](https://spring.io/security/cve-2026-59282/).
- [CVE-2026-59314: Content-Disposition y condiciones del conector](https://spring.io/security/cve-2026-59314/).
- [Avisos oficiales de seguridad de Tomcat 10](https://tomcat.apache.org/security-10.html).
- [Avisos del mantenedor de Jackson Databind](https://github.com/FasterXML/jackson-databind/security/advisories).
- [Avisos oficiales de Apache Logging](https://logging.apache.org/security.html).
