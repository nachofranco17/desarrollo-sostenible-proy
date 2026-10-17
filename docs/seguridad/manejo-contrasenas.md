# Manejo de contraseñas en memoria

## Implementación anterior

El backend utiliza Spring Security 6.5 (Spring Boot 3.5) y sesiones JDBC. Antes de
esta mejora usaba `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()`. No utiliza BCrypt.
La base de datos conserva únicamente `usuario.password_hash` como texto hexadecimal.

Las entradas de contraseñas eran:

| Entrada | Representación y procesamiento anteriores |
| --- | --- |
| `POST /api/auth/register` | `RegisterRequest.password: String`; `RegistrationService` calcula el hash. |
| `POST /api/auth/login` | Formulario `correo`/`password`; Servlet y `UsernamePasswordAuthenticationFilter` entregan `String` a `DaoAuthenticationProvider`. |
| `POST /api/auth/reauthenticate` | `ReauthenticationController.Input.password: String`; verifica el hash antes de confirmar una operación administrativa. |
| `PATCH /api/profile/me` | Contraseñas actual/nueva en `TextNode` y `FieldChange<String>`; verifica la actual y almacena el hash de la nueva. |
| `POST /api/invitations/accept` | `AcceptRequest.password: String`; reemplaza el hash de la cuenta pendiente. |
| Alta administrativa | `ProvisionCommand` obtiene `ADMIN_PASSWORD` del entorno y `CompanyProvisioner` calcula el hash. |
| Seed de desarrollo | Contraseña demo constante en `String`, utilizada en el alta y escrita en un log informativo. |
| Invitación de staff | Se calculaba un hash de un token `String` aleatorio como contraseña inicial inaccesible. |

No existe un flujo adicional de restablecimiento de contraseña por correo en el código
actual. Los tokens de invitación tienen su propio ciclo de vida y no son contraseñas.
Las contraseñas de infraestructura (JDBC/Flyway/correo/almacén de auditoría) pertenecen
a las APIs de configuración de sus bibliotecas y quedan fuera de los DTO de usuarios.

Los DTO y los nodos JSON mantenían referencias a cadenas inmutables hasta que el GC
las recogiera. El borrado de credenciales de Spring elimina referencias de la
autenticación, pero no sobrescribe el contenido de un `String` ni el formulario del
contenedor. No había una limpieza explícita de los valores de entrada.

## Cambios y propiedad de los arreglos

- Registro, reautenticación y aceptación de invitaciones reciben `char[]`.
  `PasswordDeserializer` acepta el mismo campo JSON de tipo texto y copia directamente
  desde `JsonParser.getTextCharacters()`, respetando offset y longitud. No utiliza
  `getText()`, `asText()`, `new String(password)` ni `String.valueOf(password)`.
- El login también recibe JSON directamente en `LoginRequest.password: char[]`.
  `JsonLoginAuthenticationFilter` y `CharArrayAuthenticationProvider` adaptan la
  entrada a las clases base de Spring, sin el parser Servlet de formularios ni
  `DaoAuthenticationProvider.toString()`. Se conserva el algoritmo, la ruta,
  las comprobaciones de cuentas, los eventos, CSRF y la estrategia de sesiones.
  El cliente debe enviar `application/json`; el formato de formulario anterior
  ya no autentica. Ver [verificación específica del login](verificacion-login-memoria.md).
- El PATCH usa un deserializador específico: los demás campos conservan el parser y
  la validación existentes, y las contraseñas nunca se insertan en el árbol JSON.
  Se mantienen las diferencias entre ausente, null y valor en los campos del perfil.
- Los DTO son de un solo uso y dueños de los arreglos recibidos. No se clonan en sus
  constructores; quien entrega un arreglo transfiere su uso al DTO. No deben
  reutilizarse después de llamar al servicio, ni compartirse con otras tareas.
- Servicios y controladores cierran los DTO con `try` con recursos. Su `close()` usa
  `Arrays.fill(password, '\0')`; Java ejecuta este cierre en un `finally` implícito,
  también ante denegaciones, duplicados, errores de base de datos y excepciones.
- `PasswordCleanupFilter` registra los arreglos creados durante la deserialización
  y los borra en `finally` al terminar la solicitud. Cubre errores de JSON, validación
  y autorización anteriores al controlador, campos repetidos y DTO no construidos.
  Después elimina la lista y el `ThreadLocal`. Estos endpoints son síncronos.
- `Passwords.view()` proporciona un `CharBuffer` sin copiar el arreglo. Los servicios
  no lo retienen. El encoder sólo toma prestado el `CharSequence`; el dueño del DTO
  limpia el original. Los `toString()` de los DTO ocultan sus campos.
- `NotBlankPassword` sustituye `@NotBlank`, que no admite `char[]`. Se mantienen las
  longitudes existentes: 12–128 en el alta y 128 como máximo en la reautenticación.
- La contraseña inicial de una cuenta pendiente se genera directamente en un arreglo
  con `SecureRandom` y se borra tras calcular su hash.
- El seed de desarrollo conserva sus credenciales demo conocidas, usando un arreglo
  de un solo uso. Se eliminó su impresión de la contraseña en el log.

## Hash compatible y biblioteca utilizada

El [encoder de Spring Security 6.5](https://github.com/spring-projects/spring-security/blob/6.5.x/crypto/src/main/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.java)
acepta `CharSequence`, pero internamente ejecuta `rawPassword.toString().toCharArray()`.
Pasarle solamente un `CharBuffer` dejaría una nueva cadena inmutable con la contraseña.

`CharArrayPbkdf2PasswordEncoder` implementa la misma interfaz `PasswordEncoder` y usa
la primitiva estándar JCA `SecretKeyFactory`/`PBEKeySpec`, sin implementar PBKDF2
manualmente ni agregar dependencias. Conserva exactamente la configuración anterior:

| Parámetro | Valor |
| --- | --- |
| Primitiva | `PBKDF2WithHmacSHA256` |
| Iteraciones | 310 000 |
| Salt aleatoria | 16 bytes, `SecureRandom` |
| Clave derivada | 256 bits |
| Secret/pepper adicional | Vacío, igual que antes |
| Almacenamiento | Hexadecimal de `salt || clave`, 96 caracteres |

La verificación utiliza `MessageDigest.isEqual`. No hace migraciones ni cambia hashes
existentes. Las pruebas verifican compatibilidad en ambas direcciones con el encoder
anterior, incluyendo Unicode y espacios. El resto de consumidores de `PasswordEncoder`
(por ejemplo las respuestas de actividades) conserva la interfaz y el formato.

El encoder copia por `charAt` a un arreglo temporal, lo limpia en `finally` y llama a
`PBEKeySpec.clearPassword()` para borrar la copia de la especificación. También limpia
los bytes temporales derivados e intenta destruir la clave del proveedor JCA.
Los hashes almacenados sí pueden seguir siendo `String`; no son contraseñas en claro.

## Límites explícitos

Esta mejora **reduce** la permanencia del texto plano; no garantiza su eliminación
completa de la memoria del proceso:

1. El login JSON evita las conversiones conocidas del formulario y de
   `DaoAuthenticationProvider`, pero no controla instrumentación externa,
   renderizadores del debugger, agentes ni todas las implementaciones de bibliotecas.
   Inspeccionar un `CharBuffer` mediante `toString()` puede crear una cadena con la
   contraseña desde el propio debugger. La ausencia de coincidencias entre instancias
   vivas de `String` es una observación puntual, no prueba del borrado de todo el heap.
2. Jackson y el contenedor HTTP mantienen sus propios buffers y pueden hacer copias.
   El código no modifica buffers internos de terceros. El deserializador evita
   solicitar cadenas intermedias, pero no controla el ciclo de vida de esos buffers.
3. `System.getenv("ADMIN_PASSWORD")` devuelve un `String` de la JVM. Se limpia la
   copia `char[]` usada por el alta; no se puede sobrescribir el entorno de la JVM.
   El script de PowerShell ya restaura el entorno y libera su BSTR en `finally`.
4. JDBC, Flyway, el pool y la configuración de correo requieren cadenas para sus
   credenciales de infraestructura. Cambiar los DTO de usuarios no las vuelve borrables.
5. El proveedor JCA puede conservar copias internas y no siempre permite destruir
   la clave. Tampoco se controlan copias del GC/JIT, del sistema operativo, volcados
   de memoria o terminaciones abruptas que impidan ejecutar `finally`.

No se agregan contraseñas a respuestas, mensajes de error o registros. Los handlers
existentes siguen devolviendo mensajes sin valores rechazados y la auditoría conserva
únicamente metadatos permitidos, no cuerpos ni credenciales.

## Verificación

`PasswordHandlingTest` prueba compatibilidad con el encoder anterior, que el encoder
no llama a `toString()` sobre el texto plano, deserialización por buffer, rechazo de
tipos inválidos y limpieza ante errores de binding/servicios. Las pruebas de integración
existentes verifican registro, login, reautenticación, provisión, invitaciones y cambio
de contraseña mediante HTTP real, además de autorización, CSRF y auditoría.
`JsonLoginSecurityTest` cubre las credenciales mutables, su limpieza en éxito/error,
la comprobación PBKDF2 para usuarios inexistentes, el rechazo de formularios y la
delegación a los controles de estado de cuentas y al ciclo de sesión de Spring.

Desde `backend`: `./mvnw test` (Windows: `.\mvnw.cmd test`).
