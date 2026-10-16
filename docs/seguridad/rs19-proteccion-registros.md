# RS19 — Protección de los registros de seguridad

Requerimiento de seguridad: **RS19** (ASVS v5.0.0-16.4.1, 16.4.2; documentado como R19 en el modelo)
Versión: 1.0 (16/10/2026)

Los registros de eventos de seguridad están protegidos frente a lectura, modificación o borrado no autorizados, incluso para los usuarios con más privilegios de la aplicación. Además, todo texto que termina en un registro se codifica antes de escribirse, para que no se puedan fabricar entradas falsas ni inyectar contenido en la herramienta con que se leen.

## Almacén de solo agregado (16.4.2)

Cada denegación que emite RS18 (`RegistroAccesosEstructurado`) se agrega a un **almacén separado** de la base de la aplicación:

| Aspecto | Implementación |
|---|---|
| Ubicación | Base H2 propia: `backend/.data/registros-seguridad` (`SECURITY_LOG_URL`), distinta de `xperience` |
| Tabla | `EVENTO_SEGURIDAD (ID, REGISTRADO_EN, EVENTO)`. `EVENTO` es la línea JSON de RS18 y `REGISTRADO_EN` la pone la base |
| Credenciales de la aplicación | Usuario `registro_escritor` (`SECURITY_LOG_WRITER_PASSWORD`) con **`INSERT` como único privilegio** sobre la tabla. No puede leer, modificar, borrar, vaciar ni borrar la tabla, ni crear objetos o usuarios, ni otorgarse permisos ni exportar la base |
| Credenciales de auditoría | El administrador del almacén (por ejemplo `registro_auditor`). Fuera de `dev` no forma parte de la configuración de la aplicación: solo lo conoce quien ejecuta el script de aprovisionamiento |
| Componente | `AlmacenRegistroSeguridad`: su única operación pública es `agregar`. El único bean que lo usa es `RegistroAccesosEstructurado` |
| Acceso desde la API | No existe ningún endpoint que lea los registros, así que ni un Administrador de empresa tiene un camino para verlos |
| Creación de la base | El escritor se conecta con `IFEXISTS=TRUE`, así que nunca crea una base propia (en H2, quien crea la base queda como su administrador) |

Se eligió una base separada, y no una tabla en la base de la aplicación, porque la aplicación opera su propia base con un usuario administrador. Con el almacén aparte, las credenciales con las que la aplicación modifica todos sus datos no alcanzan los registros.

### Aprovisionamiento

- **`dev` y pruebas:** el almacén se crea al iniciar (`app.security-log.provision=true`), para mantener la regla de que en local no hacen falta pasos manuales. Las credenciales de `dev` (`application-dev.properties`) son solo locales y se pueden cambiar con `SECURITY_LOG_ADMIN_USER`, `SECURITY_LOG_ADMIN_PASSWORD` y `SECURITY_LOG_WRITER_PASSWORD`.
- **Otros entornos:** se ejecuta una vez el script, que pide las dos contraseñas de forma oculta y exige que sean distintas:

  ```powershell
  .\scripts\crear-registro-seguridad.ps1            # Windows
  ```
  ```bash
  ./scripts/crear-registro-seguridad.sh             # macOS / Linux
  ```

  Después, el backend se inicia **solo** con `SECURITY_LOG_WRITER_PASSWORD`. El aprovisionamiento es idempotente: si se vuelve a ejecutar, revoca cualquier privilegio extra que tuviera el escritor y vuelve a dejarle solo `INSERT`.

### Consulta por un auditor

Con el backend en marcha (`AUTO_SERVER`), desde otra terminal y con las credenciales de auditoría:

```bash
java -cp ~/.m2/repository/com/h2database/h2/<versión>/h2-<versión>.jar org.h2.tools.Shell \
  -url "jdbc:h2:file:./backend/.data/registros-seguridad;AUTO_SERVER=TRUE;IFEXISTS=TRUE" \
  -user registro_auditor -sql "SELECT ID, REGISTRADO_EN, EVENTO FROM EVENTO_SEGURIDAD ORDER BY ID"
```

### Si el almacén no está disponible

La denegación ocurre igual y con la misma respuesta (R6). La falla queda en el log del servidor, sin credenciales. El evento también sigue emitiéndose en el logger de RS18 (`uy.edu.um.xperience.security.access`): esa copia es salida operativa del proceso, solo accesible desde el servidor y no desde la aplicación, y no contiene datos sensibles (ver RS18).

## Codificación antes de escribir (16.4.1)

`CodificadorRegistro.codificar` se aplica a **todos los valores de texto** del evento antes de escribirlo, tanto en el almacén como en el logger, y a los datos de la solicitud que aparecen en los logs de error (`PolicyEnforcementPoint`, `Denegaciones`):

- Los caracteres de control (`\r`, `\n`, `\t`, `\0`…), los separadores de línea Unicode (U+2028, U+2029) y los de formato (por ejemplo U+202E, que invierte el texto) se escriben como `\uXXXX`. Un salto de línea no puede fabricar una entrada nueva.
- Los caracteres de marcado `< > & " ' `` ` `` se escriben como `\uXXXX`. El contenido no se ejecuta en un visor HTML ni rompe el JSON.
- Cada valor se acota a 512 caracteres.

Además, RS18 ya registra solo una lista blanca de metadatos (UUID, acción del catálogo, categorías fijas, IP del contenedor): correos, búsquedas, nombres de archivo, cuerpos y encabezados no se registran. La codificación cubre igual cualquier valor que llegue al registro.

## Pruebas

`Rs19SecurityLogProtectionTest`:

1. Una denegación real (un Talento pide `/api/staff`) agrega exactamente un evento al almacén, que el auditor lee con usuario, acción y resultado, sin correo ni contraseña.
2. Con las credenciales de la aplicación fallan por **falta de permisos** (códigos H2 90096 y 90040, no por sintaxis): `SELECT`, `COUNT`, `UPDATE`, `DELETE`, `TRUNCATE`, `DROP`, `ALTER TABLE`, copiar la tabla, `GRANT`, hacerse administrador, crear usuarios y `SCRIPT` (exportar la base). El auditor confirma que no cambió nada.
3. La tabla no existe en la base de la aplicación; las credenciales de esa base no abren el almacén, y el escritor no puede crear otro almacén.
4. Solo `RegistroAccesosEstructurado` depende del almacén, y el almacén no expone otra operación pública que `agregar`.
5. Un evento con `\r\n`, un JSON falso, `<script>`, U+202E, U+2028 y un valor de 5000 caracteres se guarda en una sola línea, codificado, acotado y con `event`/`result` sin alterar.
6. El texto común (acciones, nombres con tildes, IP) no cambia.

`Rs19StoreUnavailableTest`: sin almacén aprovisionado, la solicitud sigue denegada (403) y la falla queda en el log sin la contraseña del escritor.

```bash
cd backend
./mvnw "-Dtest=Rs19*" test
```

También se verificó a mano con el jar en perfil `dev`, usando una base en archivo: tres denegaciones quedaron en el almacén; el auditor las leyó desde otro proceso, y el escritor recibió "Not enough rights" al intentar leer y borrar.

## Fuera de alcance

- **Acceso al sistema operativo:** quien tenga acceso al disco del servidor puede borrar el archivo de la base. La protección frente a eso, junto con el envío de los registros a un sistema separado y la sincronización horaria, corresponde al control C9 (registro y monitoreo), que el informe dejó fuera del alcance de este documento (sección 1.6).
- **Cadena de integridad (hash encadenado):** detectaría modificaciones hechas por el propio administrador del almacén. No se implementó porque el requerimiento pide proteger frente a la aplicación y sus usuarios, y eso lo cubren los privilegios.
