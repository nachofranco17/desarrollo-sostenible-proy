# XPerience

Plataforma de formación y empleabilidad. Esta entrega implementa el registro de Talentos y el inicio/cierre de sesión para cuentas habilitadas de Talento, Administrador, Reclutador y Editor.

El registro público crea **únicamente Talentos**. Las empresas y su primer Administrador se crean mediante un script de desarrollo. No hay formulario ni endpoint público de alta de empresas. El Administrador puede invitar al resto del staff y asignar sus roles desde **Mi cuenta → Gestionar staff** ([RF7: guía de prueba y correo](docs/sprint1/rf7-invitaciones-staff.md)). Conforme a `docs/seguridad`, el staff necesita una membresía activa y un rol asignado para iniciar sesión y operar. El Administrador debe reautenticarse antes de asignar roles.

## Ejecutar en Windows sin Docker

Requisitos: JDK 17 o superior, Node.js 22 y npm. Maven Wrapper está incluido y descarga Maven en su primer uso. Si `JAVA_HOME` está configurado, debe apuntar a un JDK.

Desde la raíz del repositorio, en una terminal PowerShell:

```powershell
cd backend
.\mvnw.cmd package
java -jar target/xperience.jar --spring.profiles.active=dev
```

En otra terminal, también desde la raíz:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Abrir **http://127.0.0.1:5173**. El backend escucha en el puerto 8081. Vite reenvía `/api` al backend para que las cookies y CSRF funcionen en el mismo origen. Si necesitás otro puerto, configurá `PORT` en el backend y `BACKEND_URL` en la terminal del frontend.

El perfil `dev` utiliza H2 persistente en `backend/.data/`, con compatibilidad PostgreSQL. Las cuentas sobreviven al reinicio. Es exclusivamente una configuración local; no requiere crear usuarios ni bases manualmente. Flyway aplica las migraciones al iniciar.

## Alta de empresa y Administrador

Después de compilar el backend, ejecutar desde la raíz:

```powershell
.\scripts\crear-empresa.ps1 -Empresa 'Empresa Demo' -Correo 'admin@example.test' -Nombre 'Ana' -Apellido 'Prueba'
```

El script pide la contraseña de forma oculta: entre 12 y 128 caracteres. No se pasa por argumentos ni se guarda en el repositorio. Luego se puede ingresar desde el mismo formulario que utiliza un Talento.

El alta crea empresa, usuario STAFF, membresía ADMIN activa y un registro en `alta_empresa`, dentro de una única transacción. Si el correo ya existe o los datos son inválidos, falla sin crear una empresa parcial ni modificar cuentas existentes. El script puede ejecutarse con el backend local abierto.

## Cómo probar el requisito

1. En **Registrate como Talento**, completar nombre, apellido, correo y contraseña. Se muestra un mensaje genérico y no se inicia sesión automáticamente.
2. Ingresar con esos datos. Se abre `/mi-cuenta`, con el rol Talento.
3. Presionar **Verificar acceso** y recargar la página. La cuenta se obtiene de la ruta protegida `GET /api/account`.
4. Cerrar sesión y volver a `/mi-cuenta`: redirige al ingreso. Una llamada directa a `/api/account` devuelve 401.
5. Ejecutar el script anterior, ingresar como Administrador y repetir los pasos 3 y 4. La cuenta muestra su empresa y rol.
6. Comprobar que el registro solo dice Talento y no ofrece selección de rol ni alta de empresas. La API rechaza campos extra, por ejemplo `rol`, `tipoCuenta` o `empresaId`.
7. Repetir el registro con el mismo correo, incluso en mayúsculas: devuelve el mismo mensaje y estado HTTP 202 que un alta nueva. No reemplaza los datos ni la contraseña de la cuenta original.
8. Ingresar con una contraseña incorrecta o un correo inexistente: ambos casos muestran **Correo o contraseña incorrectos** y la API devuelve 401.

### Pruebas automáticas

```powershell
cd backend
.\mvnw.cmd test
```

Las pruebas de integración levantan un servidor HTTP real y una base H2 en memoria. Comprueban cookies, rotación de sesión al ingresar, invalidación al salir, credenciales incorrectas, duplicados, registros concurrentes, rechazo de campos de privilegios, CSRF, alta transaccional y revocación de acceso. Las pruebas MockMvc verifican el PEP, las denegaciones, las consultas filtradas y la reautenticación. Otra prueba compara los 54 permisos persistidos con la tabla de `docs/seguridad/modelo-autorizacion.md`.

`PostgresAuthIntegrationTest` repite los escenarios HTTP con Testcontainers y PostgreSQL cuando Docker está disponible. Si no lo está, se informa como omitida. No requiere configurar una base manualmente.

Con el backend **dev** ejecutándose y el JAR actualizado, desde otra terminal:

```powershell
cd frontend
npm.cmd run build
npm.cmd run test:e2e
```

Las pruebas E2E usan Google Chrome instalado y PowerShell en Windows. Playwright inicia Vite si hace falta. Cubren el registro, el acceso protegido, logout, duplicados, presentación móvil y el **script real** de alta seguido del login/logout del Administrador. Crean cuentas de prueba únicas en la base local; no eliminan datos existentes.

## PostgreSQL

El perfil predeterminado utiliza PostgreSQL. Para levantar una base local con Docker, configurar `DB_PASSWORD` en el entorno y ejecutar:

```powershell
$env:DB_PASSWORD = 'elegir-una-clave-local'
docker compose up -d db
```

Desde `backend`, en la misma terminal:

```powershell
$env:COOKIE_SECURE = 'false' # Solo para probar mediante HTTP en localhost.
java -jar target/xperience.jar
```

`DB_URL` y `DB_USER` son opcionales; sus valores predeterminados son `jdbc:postgresql://localhost:5432/xperience` y `xperience`. Para dar de alta una empresa en esa base, ejecutar el script con `-Perfil postgres` y las mismas variables de conexión.

Fuera del perfil local, las cookies requieren HTTPS por defecto. En un despliegue, servir el frontend compilado y `/api` bajo el mismo origen. No exponer el servidor de desarrollo de Vite. El entorno de esta entrega no tenía Docker en ejecución: la verificación automatizada se realizó con H2, no con PostgreSQL.

## Implementación y alcance

- Backend: Spring Boot/Security, Spring Data JPA con Specifications, Flyway y Spring Session JDBC.
- Frontend: React, TypeScript y Vite. No guarda credenciales ni tokens de sesión en localStorage.
- Contraseñas: PBKDF2 con sal aleatoria y parámetros de Spring Security 5.8; nunca se devuelven por la API.
- Sesiones: cookie HttpOnly, SameSite=Lax, expiración por 30 minutos de inactividad y protección CSRF también en login/logout.
- Autorización: `@RequiereAccion` por endpoint de negocio, PEP central y evaluador de permisos vigente por solicitud. Login y logout atraviesan el mismo evaluador desde un filtro. Las lecturas de datos de la API usan Specifications y se deniega por defecto.
- Cambio de roles: reautenticación del Administrador mediante `POST /api/auth/reauthenticate`, válida por cinco minutos y vinculada a su sesión. Se rechazan cambios sobre la propia membresía y sobre otras empresas.
- Denegaciones: todas las rutas de rechazo invocan `RegistroAccesos`. Su implementación predeterminada no persiste eventos mientras R18/R19 estén pendientes, tal como permite el diseño.

El catálogo completo de permisos está cargado. Hay módulos de perfiles, gestión de cursos y proyectos e invitaciones del staff. Siguen pendientes ofertas, postulaciones, inscripciones y recuperación/verificación de correo de Talentos. Los alcances que requieren inscripciones o postulaciones reales se deniegan hasta implementar esos recursos y sus consultas. No se agregan endpoints ficticios ni se aceptan relaciones de propiedad enviadas por el cliente.

Ver [contrato HTTP y decisiones de alcance](docs/autenticacion.md) y [permisos mínimos y sus pruebas](docs/permisos-minimos.md).
