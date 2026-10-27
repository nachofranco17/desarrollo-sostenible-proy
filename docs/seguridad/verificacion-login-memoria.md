# Login: flujo, memoria y verificación con IntelliJ

## Conclusión y alcance

Es viable eliminar las conversiones conocidas a `String` de la contraseña en el
camino normal de login. Para ello se recibe JSON directamente en `char[]`, se adapta
la comprobación de credenciales a las clases base de Spring y se conserva PBKDF2.
No se puede prometer que ninguna copia exista en todo el proceso ni que los bytes
físicos desaparezcan del heap. Se deben distinguir estas tres comprobaciones:

1. El código de aplicación no solicita un `String` con la contraseña del login.
2. Los arreglos que controla se sobrescriben, también cuando hay errores.
3. En una observación concreta, el debugger no encuentra una instancia viva de
   `String` con ese valor. Esto no demuestra por sí solo los puntos anteriores ni
   la ausencia de copias en `char[]`, `byte[]` o memoria nativa.

## Dónde se creaban las cadenas en el login anterior

El cliente enviaba `application/x-www-form-urlencoded` a `/api/auth/login`.

| Etapa | Tratamiento de la contraseña y retención posible |
| --- | --- |
| Navegador | El formulario y JavaScript mantienen cadenas en el proceso del navegador. No son objetos del heap Java del backend. |
| Tomcat: `Request.parseParameters()` → `Parameters.processParameters()` | Decodifica los parámetros y crea el `String` del valor mediante `tmpValue.toString()`. El mapa de parámetros lo referencia durante la solicitud. |
| `UsernamePasswordAuthenticationFilter.obtainPassword()` | Obtiene ese valor por `request.getParameter("password")` y lo entrega al token. No necesariamente crea otra cadena: puede ser la misma referencia. |
| `DaoAuthenticationProvider.additionalAuthenticationChecks()` | Asigna `getCredentials().toString()` a `presentedPassword`. Con un `String`, `toString()` devuelve la misma referencia. Con un `CharBuffer`, sí produciría otra cadena con el texto. |
| `DaoAuthenticationProvider.createSuccessAuthentication()` | También llama a `toString()` antes de sus comprobaciones opcionales. Cambiar sólo el chequeo de contraseña no basta. |
| Usuario inexistente | El método privado `mitigateAgainstTimingAttack()` también llama a `toString()`. Cambiar sólo el caso exitoso tampoco basta. |
| Encoder de la mejora anterior | El adapter JCA ya evitaba pedir otra cadena y limpiaba sus arreglos; no podía borrar la cadena creada por Servlet. |
| Finalización | `ProviderManager.eraseCredentials()` elimina referencias del resultado autenticado; no sobrescribe un `String`. El token de entrada, los frames y los parámetros de la solicitud pueden seguir referenciándolo hasta salir/reciclarse. Después depende del GC. |

La creación de Tomcat puede comprobarse en su
[parser de parámetros](https://github.com/apache/tomcat/blob/10.1.x/java/org/apache/tomcat/util/http/Parameters.java).
Las tres llamadas del provider están en el
[código de Spring Security](https://github.com/spring-projects/spring-security/blob/6.5.11/core/src/main/java/org/springframework/security/authentication/dao/DaoAuthenticationProvider.java).
El contexto de sesión no estaba diseñado para almacenar la contraseña en claro;
encontrar una cadena después de autenticar no significa automáticamente que esté
guardada en la sesión. Hay que inspeccionar quién la referencia.

## Flujo implementado

```text
POST /api/auth/login (application/json, header CSRF)
  → filtros de Spring: contexto, CSRF y política existente de sesion.iniciar
  → JsonLoginAuthenticationFilter (AbstractAuthenticationProcessingFilter)
  → Jackson InputStream → PasswordDeserializer → LoginRequest.password: char[]
  → UsernamePasswordAuthenticationToken con credenciales char[]
  → ProviderManager
  → CharArrayAuthenticationProvider (AbstractUserDetailsAuthenticationProvider)
  → UserDetailsService existente: búsqueda, normalización de correo y cuenta activa
  → comprobaciones base de Spring y PasswordEncoder.matches(CharBuffer, hash)
  → PBKDF2 JCA compatible con hashes existentes
  → ProviderManager elimina credenciales del resultado
  → finally: token de entrada sin credenciales y DTO sobrescrito con '\0'
  → estrategia compuesta de sesión de Spring, persistencia del contexto y handler 204
  → finally exterior: limpieza de arreglos deserializados y ThreadLocal
```

El filtro lee el stream, no usa `getParameter()`, no construye el body como `String`
y no pasa la contraseña por un `JsonNode`. Jackson crea cadenas para nombres de
campos y para el correo; la mejora se refiere al **valor de la contraseña**.

El provider implementa sólo los puntos de extensión de carga del usuario y cotejo
de credenciales de la clase base. Spring sigue comprobando cuenta bloqueada,
deshabilitada, expirada y credenciales expiradas, ocultando usuarios inexistentes
y creando el resultado autenticado. Para un usuario inexistente se ejecuta un
cotejo PBKDF2 contra un hash ficticio, conservando la mitigación temporal existente;
esto no equivale a prometer tiempos idénticos en todos los escenarios.

La configuración utiliza el `ProviderManager` de Spring sin provider alternativo
de formulario. Conecta explícitamente el repositorio de contexto y la estrategia
compuesta de sesión que construye Spring, incluidos el cambio de ID y el tratamiento
de CSRF al autenticar. No se hace un login manual desde un controlador ni se escribe
a mano una cookie de autenticación. El ciclo de vida está documentado en la
[arquitectura de autenticación de Spring](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html).

La ruta, campos, roles, cookies, respuestas y base de datos se conservan. Cambia el
**formato de entrada**: el frontend y los clientes de prueba ahora envían JSON.
Los clientes externos deben migrar; un formulario anterior devuelve el error
genérico 401 y no entra al manager de autenticación. Un login sin CSRF sigue denegado.
Los campos adicionales, tipos incorrectos y JSON con tokens sobrantes también se
rechazan sin adjuntar el error del parser a la respuesta de credenciales.

PBKDF2-HMAC-SHA256 conserva 310 000 iteraciones, salt de 16 bytes y clave de 256 bits,
con `salt || hash` hexadecimal de 96 caracteres. No cambió el encoder durante esta
revisión ni se implementó una función criptográfica manual. Las pruebas usan hashes
del encoder anterior para verificar el nuevo flujo.

## Límites que siguen existiendo

- Servlet, Jackson, HTTP/TLS y JCA administran buffers y copias que no controla la
  aplicación. Un error de sintaxis puede hacer que una biblioteca construya mensajes
  internos con fragmentos de entrada, aunque el filtro no los propague ni registre.
- El proveedor SunJCE de la familia OpenJDK 17 convierte caracteres a UTF-8 usando
  buffers, sin necesitar un `String` de la contraseña en ese camino. Aun así,
  `PBKDF2KeyImpl` conserva una copia de caracteres y su soporte de destrucción/limpieza
  depende de la versión. `clearPassword()` de la especificación no borra todas las
  copias del proveedor. Ver el [código de OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/com/sun/crypto/provider/PBKDF2KeyImpl.java).
- No se usan reflexión sobre campos privados del JDK, `Unsafe`, internado de la
  contraseña, un proveedor criptográfico artesanal ni GC forzado en producción.
- Las contraseñas de entorno, conexiones JDBC y otros usos de `String` externos al
  login siguen teniendo los límites descritos en [manejo-contrasenas.md](manejo-contrasenas.md).
- Renderizadores, evaluaciones y watches del debugger pueden crear y retener objetos
  nuevos. Tampoco se controlan copias históricas del GC/JIT o memoria ya liberada.
- Si la contraseña coincide con otro dato que se guarda como texto, como un correo,
  esa cadena puede existir por su uso como dato ordinario. Para la evaluación se
  necesita una contraseña única que no aparezca en otras fuentes de la JVM.

Mantener el formulario estándar y documentar su límite era una alternativa con menos
cambios, pero no quitaba la cadena del parámetro. Parsear manualmente formularios
URL-encoded añadiría otro parser de credenciales y sus casos de codificación; se
prefirió el soporte JSON de Jackson. Sustituir PBKDF2 no resuelve por sí solo estas
fronteras y haría innecesariamente más amplio el cambio.

## Verificación paso a paso en IntelliJ

### Preparar una observación que no cree la contraseña desde el debugger

1. Reiniciá el backend y ejecutá **Debug** sobre `XperienceApplication`, con el
   perfil y configuración local habituales. Usá una contraseña de prueba única,
   registrada desde el navegador. No uses la contraseña fija del seed, una constante
   de un test ni `ADMIN_PASSWORD` del entorno: esas fuentes pueden conservar cadenas
   independientes del login. No depures cliente HTTP y backend dentro de la misma JVM.
2. Antes de autenticar, en **Settings → Build, Execution, Deployment → Debugger →
   Data Views → Java**, desactivá **Enable 'toString()' object view** y cualquier
   renderer propio que convierta arreglos o `CharBuffer` a texto. Limpiá watches de
   sesiones anteriores. Los renderizadores pueden ejecutar métodos en el programa,
   como explica [JetBrains](https://www.jetbrains.com/help/idea/stepping-through-the-program.html).
3. No evalúes `new String(input.password())`, `Passwords.view(...).toString()` ni
   una condición como `this.equals("la contraseña exacta")`: esa evaluación puede
   crear precisamente la cadena buscada dentro de la JVM observada.

### Comprobar el arreglo y su limpieza

4. Poné un breakpoint de línea en `JsonLoginAuthenticationFilter.attemptAuthentication()`,
   después de deserializar y antes de `authenticate(token)`. Iniciá sesión desde la
   interfaz. En Variables verificá que `input.password` y las credenciales de `token`
   son `char[]`. Expandí el arreglo; no lo conviertas a texto. Si necesitás ver clases
   de bibliotecas, habilitá **Show All Frames**.
5. Poné otro breakpoint en `Passwords.clear()`. Identificá el arreglo del DTO y avanzá
   sobre `Arrays.fill`. Al volver al llamador, el arreglo original debe contener sólo
   `\u0000`. Las llamadas del encoder limpian otros arreglos también; no confundas
   una copia temporal con el arreglo original. Se puede marcar el arreglo para esta
   comprobación y quitar la marca antes de otra medición.
6. Cerrá sesión o usá otra ventana privada y repetí con contraseña incorrecta y con
   usuario inexistente. La política existente no permite iniciar otro login estando
   autenticado. Ambos intentos como visitante deben terminar
   con el mismo error genérico; el arreglo de entrada debe quedar sobrescrito.

### Buscar instancias de String después del login

7. Reanudá hasta recibir el 204 y abrir Mi cuenta. Para salir completamente del stack
   del login, colocá un breakpoint en `AuthController.current()`, pedí `GET /api/account`
   recargando la página y detenete allí. La contraseña del login ya no debe ser una
   variable local de ese frame.
8. En la ventana **Debug**, activá **Memory** desde **Layout Options**. Hacé clic en
   **Load classes**, buscá `java.lang.String` y hacé doble clic para abrir sus instancias.
   La columna Count y esa lista corresponden a objetos vivos, según la
   [documentación de Memory de IntelliJ](https://www.jetbrains.com/help/idea/analyze-objects-in-the-jvm-heap.html).
9. Usá una condición que compare longitud y caracteres sin construir un `String`
   con la contraseña. Podés generar la condición en una terminal PowerShell externa
   al backend y pegar sólo el resultado en **Condition**:

   ```powershell
   $probePassword = Read-Host 'Contraseña de prueba usada en el navegador'
   $checks = for ($i = 0; $i -lt $probePassword.Length; $i++) {
       'this.charAt({0}) == {1}' -f $i, [int][char]$probePassword[$i]
   }
   'this.length() == {0} && {1}' -f $probePassword.Length, ($checks -join ' && ')
   Remove-Variable probePassword, checks
   ```

   Esto produce `this.length() == N && this.charAt(0) == ...`: sólo enteros y llamadas
   de lectura sobre la instancia. La cadena escrita en la terminal está en el proceso
   de PowerShell, no en el heap del backend. El resultado de la búsqueda debe ser cero
   coincidencias exactas en el flujo normal probado; debe medirse, no asumirse.
10. Para revisar también cadenas mayores que pudieran contener el body completo,
    usá por separado una condición amplia como `this.length() >= N` y revisá los
    candidatos, incluyendo el contenido de cada instancia,
    sin introducir un literal con la contraseña en `contains()` o `indexOf()`.
    Si aparece una coincidencia, usá **Show Referring Objects** y anotá su origen:
    parámetros Servlet, un agente, una evaluación, entorno, fixture o biblioteca.
    No la ocultes ni la borres manualmente antes de registrar la observación.
11. Hacé la primera captura sin solicitar GC. Si repetís después de GC, anotá que es
    otra condición de medición: que el GC recoja una cadena no demuestra que se haya
    sobrescrito ni que nunca haya existido. Repetí con el error de login y registrá
    versión de JDK, contraseña de prueba identificada sin publicarla, estado HTTP,
    instante y coincidencias encontradas.

La captura permite decir **«no observé instancias vivas de String con esta contraseña
en este punto»**. No permite decir **«la contraseña desapareció completamente de memoria»**.

## Pruebas automáticas

`JsonLoginSecurityTest` verifica que el filtro no consulta parámetros Servlet,
utiliza `char[]`, limpia arreglos y referencias al token tanto en éxito como en error,
ejecuta el cotejo costoso para usuarios inexistentes y conserva los chequeos de estado
de cuentas. También rechaza cuerpos y formatos no admitidos.

`AuthIntegrationTest` conserva el login HTTP real, sesiones, roles, cookies y CSRF;
comprueba el cambio de identificador de sesión y agrega el rechazo del formulario
anterior y de campos privilegiados. El resto de clientes de prueba usa el mismo
contrato JSON. Las pruebas funcionales no son por sí solas un inventario del heap.

## Evidencia obtenida el 16 de octubre de 2026

- `mvn package`: **508 pruebas**, cero fallos/errores/omisiones; JAR empaquetado.
- `npm run build`: TypeScript y build de Vite completados.
- Playwright: registro de Talento, contraseña incorrecta, login, sesión persistida,
  ruta protegida y logout desde el formulario real: **1 prueba pasada**.
- Inspección adicional mediante JDI contra una JVM del backend separada:
  Java HotSpot 17.0.8, Spring Boot 3.5.16, Spring Security 6.5.11 y Tomcat 10.1.61.

El cliente de inspección creó una cuenta con una contraseña aleatoria de 40 caracteres
generada en **su propio proceso**, envió solicitudes HTTP reales y consultó las
instancias de `java.lang.String` del backend con
[`ReferenceType.instances(0)`](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/ReferenceType.html#instances(long)).
Las comparaciones se realizaron en el proceso inspector; no se evaluó un literal con
la contraseña en la JVM del servidor ni se solicitó `System.gc()`. Se suspendieron
sus threads durante cada enumeración y se confirmó la presencia del correo único
como control de que se estaba leyendo el proceso y las instancias adecuados.

| Instante | String enumerados | Iguales a la contraseña | Contienen la contraseña | Control del correo |
| --- | ---: | ---: | ---: | ---: |
| Después del registro, HTTP 202 | 72 520 | 0 | 0 | 1 |
| Después del login 204 y consulta de cuenta 200 | 72 575 | 0 | 0 | 1 |
| Después de logout 204 y login incorrecto 401 | 72 758 | 0 | 0 | 1 |

La búsqueda final también comprobó la contraseña original como subcadena de otras
cadenas, incluido el valor incorrecto que contenía esa contraseña con un sufijo.
No hubo referencias reportadas como recolectadas durante las enumeraciones. El servidor
de prueba utilizó bases H2 en memoria y se detuvo al finalizar.

Esta fue una observación por JDI, **no una sesión de IntelliJ ejecutada por el agente**.
IntelliJ puede repetir la inspección con los pasos anteriores. Los conteos cambiarán
en otros arranques; el resultado demuestra únicamente que no se observaron esas
coincidencias en esas capturas, no el borrado total de memoria ni una garantía para
otras versiones, proveedores o configuraciones de instrumentación.
