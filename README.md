# XPerience

Plataforma de formación y empleabilidad. Esta entrega (Sprint 1) cubre:

- **RF1** — registro público de Talentos e inicio/cierre de sesión (Talento, Administrador, Reclutador y Editor)
- **RF2** — perfil del Talento ([guía](docs/sprint1/rf2-perfil-talento.md))
- **RF5** — gestión de cursos y proyectos por la empresa ([guía](docs/sprint1/rf5-gestion-cursos-proyectos.md))
- **RF7** — invitaciones y asignación de roles del staff ([guía](docs/sprint1/rf7-invitaciones-staff.md))

El registro público crea **únicamente Talentos**. Las empresas y su primer Administrador se crean mediante un script de desarrollo. No hay formulario ni endpoint público de alta de empresas. El Administrador invita al resto del staff y asigna roles desde **Mi cuenta → Gestionar staff**. Conforme a `docs/seguridad`, el staff necesita una membresía activa y un rol asignado para iniciar sesión y operar. El Administrador debe reautenticarse antes de asignar roles.

## Ejecutar en local

Requisitos: JDK 17 o superior, Node.js 22 y npm. Maven Wrapper está incluido. Si `JAVA_HOME` está configurado, debe apuntar a un JDK.

### Windows (PowerShell)

```powershell
cd backend
.\mvnw.cmd package
java -jar target/xperience.jar --spring.profiles.active=dev
```

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

### macOS / Linux

```bash
cd backend
./mvnw package
java -jar target/xperience.jar --spring.profiles.active=dev
```

```bash
cd frontend
npm ci
npm run dev
```

Abrir **http://127.0.0.1:5173**. El backend escucha en el puerto **8081**. Vite reenvía `/api` al backend para que las cookies y CSRF funcionen en el mismo origen. Si necesitás otro puerto, configurá `PORT` en el backend y `BACKEND_URL` en la terminal del frontend.

La base es **H2** persistente en `backend/.data/` (archivo `xperience`). Las cuentas sobreviven al reinicio. Flyway aplica las migraciones al iniciar. El perfil `dev` escribe las invitaciones RF7 en disco (sin SMTP). También:

- invitaciones RF7 → `backend/.data/invitaciones/`
- materiales de cursos RF5 → `backend/.data/archivos` (configurable con `STORAGE_DIR`)

No requiere crear usuarios ni bases manualmente. No hace falta Docker.

## Alta de empresa y Administrador

Después de compilar el backend (`mvn package`), ejecutar desde la raíz.

**Windows (PowerShell):**

```powershell
.\scripts\crear-empresa.ps1 -Empresa 'Empresa Demo' -Correo 'admin@example.test' -Nombre 'Ana' -Apellido 'Prueba'
```

**macOS / Linux** (detené el backend antes; H2 no permite dos procesos a la vez):

```bash
./scripts/crear-empresa.sh 'Empresa Demo' 'admin@example.test' 'Ana' 'Prueba'
```

El script pide la contraseña de forma oculta (12 a 128 caracteres).

La contraseña debe tener entre 12 y 128 caracteres. No se pasa por argumentos de línea de comandos ni se guarda en el repositorio. Luego se puede ingresar desde el mismo formulario que utiliza un Talento.

El alta crea empresa, usuario STAFF, membresía ADMIN activa y un registro en `alta_empresa`, dentro de una única transacción. Si el correo ya existe o los datos son inválidos, falla sin crear una empresa parcial ni modificar cuentas existentes. Con H2 (`AUTO_SERVER`), el script puede ejecutarse con el backend local abierto.

## Cómo probar

### Registro e inicio de sesión (RF1)

1. En **Registrate como Talento**, completar nombre, apellido, correo y contraseña. Se muestra un mensaje genérico y no se inicia sesión automáticamente.
2. Ingresar con esos datos. Se abre `/mi-cuenta`, con el rol Talento.
3. Presionar **Verificar acceso** y recargar la página. La cuenta se obtiene de la ruta protegida `GET /api/account`.
4. Cerrar sesión y volver a `/mi-cuenta`: redirige al ingreso. Una llamada directa a `/api/account` devuelve 401.
5. Crear un Administrador con el script anterior, ingresar y repetir los pasos 3 y 4. La cuenta muestra su empresa y rol.
6. Comprobar que el registro solo dice Talento y no ofrece selección de rol ni alta de empresas. La API rechaza campos extra, por ejemplo `rol`, `tipoCuenta` o `empresaId`.
7. Repetir el registro con el mismo correo, incluso en mayúsculas: devuelve el mismo mensaje y estado HTTP 202 que un alta nueva. No reemplaza los datos ni la contraseña de la cuenta original.
8. Ingresar con una contraseña incorrecta o un correo inexistente: ambos casos muestran **Correo o contraseña incorrectos** y la API devuelve 401.

### Perfil, cursos y staff

- **RF2:** desde Mi cuenta → **Ver mi perfil** (`/perfil`). Detalle en [docs/sprint1/rf2-perfil-talento.md](docs/sprint1/rf2-perfil-talento.md).
- **RF5:** con Administrador o Editor → **Gestionar cursos y proyectos** (`/gestion`). Detalle en [docs/sprint1/rf5-gestion-cursos-proyectos.md](docs/sprint1/rf5-gestion-cursos-proyectos.md).
- **RF7:** con Administrador → **Gestionar staff** (`/staff`). En `dev`, abrir el enlace desde `backend/.data/invitaciones/`. Aceptar la invitación **no** asigna rol: hasta que el Admin asigne uno (reautenticándose), el invitado no puede iniciar sesión. Detalle en [docs/sprint1/rf7-invitaciones-staff.md](docs/sprint1/rf7-invitaciones-staff.md).

### Pruebas automáticas

```bash
cd backend
./mvnw test
```

En Windows: `.\mvnw.cmd test`.

Las pruebas de integración levantan un servidor HTTP real y una base H2 en memoria. Cubren auth, perfil (RF2), cursos (RF5), invitaciones/staff (RF7), autorización por instancia (RS11) y registro estructurado de denegaciones (RS18). También hay pruebas MockMvc del PEP, contratos de política, fallo del logger sin fail-open, y una comparación de los 54 permisos persistidos con la tabla de `docs/seguridad/modelo-autorizacion.md`.

Con el backend **dev** ejecutándose y el JAR actualizado, desde otra terminal:

```bash
cd frontend
npm ci
npm run test:e2e
```

Las pruebas E2E usan Google Chrome instalado. Playwright inicia Vite (`npm run dev`) si hace falta. En Windows el fixture de alta usa PowerShell; en macOS/Linux usa el script bash equivalente. Cubren registro/acceso/logout, invitaciones de staff y el alta real de Administrador. Crean cuentas de prueba únicas en la base local; no eliminan datos existentes.

## Implementación y alcance

- Backend: Spring Boot/Security, Spring Data JPA con Specifications, Flyway, Spring Session JDBC y H2.
- Frontend: React, TypeScript y Vite. No guarda credenciales ni tokens de sesión en localStorage.
- Contraseñas: PBKDF2 con sal aleatoria y parámetros de Spring Security 5.8; nunca se devuelven por la API.
- Sesiones: cookie HttpOnly, SameSite=Lax, expiración por 30 minutos de inactividad y protección CSRF también en login/logout.
- Autorización: `@RequiereAccion` por endpoint de negocio, PEP central y evaluador de permisos vigente por solicitud. Login y logout atraviesan el mismo evaluador desde un filtro. Las lecturas de datos de la API usan Specifications y se deniega por defecto.
- Cambio de roles: reautenticación del Administrador mediante `POST /api/auth/reauthenticate`, válida por cinco minutos y vinculada a su sesión. Se rechazan cambios sobre la propia membresía y sobre otras empresas.
- Denegaciones: todas las rutas de rechazo invocan `RegistroAccesos`. RS18 emite un evento JSON estructurado con metadatos seguros ([docs/seguridad/rs18-registro-accesos-denegados.md](docs/seguridad/rs18-registro-accesos-denegados.md)); RS19 (protección del almacén) sigue pendiente.
- Archivos de curso: disco local (`STORAGE_DIR`); el modelo prevé MinIO más adelante.

El catálogo completo de permisos está cargado. Hay módulos de perfiles, gestión de cursos y proyectos e invitaciones del staff. Siguen pendientes ofertas, postulaciones, inscripciones, descarga de material para Talentos inscriptos (RF4) y recuperación/verificación de correo. Los alcances que requieren inscripciones o postulaciones reales se deniegan hasta implementar esos recursos y sus consultas. No se agregan endpoints ficticios ni se aceptan relaciones de propiedad enviadas por el cliente.

Ver también:

- [Contrato HTTP y decisiones de alcance](docs/autenticacion.md)
- [Permisos mínimos y sus pruebas](docs/permisos-minimos.md)
- [Modelo de autorización](docs/seguridad/modelo-autorizacion.md)
- [RS11 — autorización por instancia](docs/seguridad/rs11-autorizacion-por-instancia.md)
- [RS18 — registro de accesos denegados](docs/seguridad/rs18-registro-accesos-denegados.md)
