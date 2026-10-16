# Mínimo privilegio en la base de datos principal

Referencia: Práctica 16, "Avoid granting excess privileges", sección 5.4 (permisos mínimos en base de datos) y tabla de controles recomendados (capa "Base de datos").
Versión: 1.0 (16/10/2026)

La aplicación atiende las solicitudes con un usuario de base de datos que solo puede manipular datos. Las migraciones las aplica otro usuario, dueño del esquema. Aunque alguien lograra ejecutar SQL con las credenciales de la aplicación (por ejemplo, con una inyección), no podría cambiar el esquema, administrar usuarios, leer archivos del servidor, ejecutar código Java dentro de la base ni darse permisos escribiendo en la tabla `permiso`.

## Problema anterior

La aplicación se conectaba como `sa`, el administrador de H2, y con esa misma conexión Flyway aplicaba las migraciones. Es el caso "inseguro" de la práctica: un `GRANT ALL` sobre toda la base. Un error o una inyección en cualquier consulta tenía el alcance de un administrador:

| Con `sa` se podía | Consecuencia |
|---|---|
| `DROP TABLE`, `ALTER TABLE`, `CREATE TABLE` | Borrar o alterar el esquema |
| `INSERT INTO permiso …` | Darle a un Talento acciones de Administrador: la autorización del backend lee esa tabla |
| `CREATE USER`, `GRANT`, `ALTER USER … ADMIN TRUE` | Crear accesos persistentes a la base |
| `CALL FILE_READ('…')`, `SCRIPT TO '…'` | Leer archivos del servidor o exportar toda la base |
| `CREATE ALIAS … FOR 'clase.metodo'` | Ejecutar código Java dentro del proceso |

Los controles de la aplicación (PEP, `@RequiereAccion`, autorización por instancia) no cubren este caso, porque actúan antes de llegar a la base.

## Diseño

| Usuario | Configuración | Quién lo usa | Privilegios |
|---|---|---|---|
| Dueño del esquema | `DB_OWNER_USER` / `DB_OWNER_PASSWORD` (por defecto `sa` sin contraseña, la base local actual) | Solo Flyway, al arrancar | Administrador: crea tablas y usuarios |
| Aplicación | `DB_USER` / `DB_PASSWORD` (por defecto `xperience_app`) | El pool con el que se atienden todas las solicitudes | Solo los de la tabla siguiente |

| Tabla | Privilegios de la aplicación | Motivo |
|---|---|---|
| Tablas de negocio y de sesiones (`usuario`, `membresia`, `curso`, `postulacion`, `spring_session`…) | `SELECT, INSERT, UPDATE, DELETE` | Lo que usan los módulos |
| `permiso` | `SELECT` | El catálogo de permisos solo cambia por migraciones. Si la aplicación pudiera escribirlo, podría escalar privilegios |
| `alta_empresa` | `SELECT, INSERT` | Constancia del alta de empresas: se agrega, pero no se corrige ni se borra |
| `flyway_schema_history` | Ninguno | Solo le interesa a Flyway |

El usuario de la aplicación no es administrador y no es dueño de ningún esquema. Por eso H2 le rechaza todo lo que no sea manipular datos (DDL, usuarios, `GRANT`, `FILE_READ`, `CREATE ALIAS`, `SCRIPT`, `SET` de la base). `TRUNCATE` cuenta como un `DELETE` de todas las filas, así que solo se puede usar donde ya había `DELETE`.

### Implementación

- `PrivilegiosBaseDatos` (`backend/src/main/java/uy/edu/um/xperience/persistence`) es la estrategia de migración de Flyway. En cada arranque:
  1. Abre una conexión del dueño y ejecuta `flyway.migrate()`.
  2. Crea el usuario de la aplicación si no existe, actualiza su contraseña, le quita el rol de administrador y, para cada tabla, revoca todo y vuelve a otorgar exactamente lo de la tabla anterior. Las tablas nuevas que agregue una migración reciben automáticamente `SELECT, INSERT, UPDATE, DELETE`. Si una tabla necesita menos, se agrega a `EXCEPCIONES`.
  3. Abre el pool de la aplicación y recién después suelta la conexión del dueño. Así comprueba que el usuario limitado puede entrar, y además evita un bug de H2
- Ajustar los privilegios es idempotente: si alguien le dio a mano un privilegio extra a `xperience_app`, el próximo arranque lo revoca.
- La aplicación **no arranca** si falta `DB_PASSWORD`, si el usuario tiene un nombre inválido o si es el mismo que el dueño. No sigue con un usuario sin ajustar.
- La URL de la aplicación lleva `IFEXISTS=TRUE`, así que el usuario de la aplicación nunca crea la base. En H2, quien crea la base queda como su administrador; es el mismo cuidado que en RS19.
- En H2, los parámetros de la URL se aplican como `SET`, y algunos (por ejemplo `DB_CLOSE_DELAY`) requieren ser administrador. Por eso la URL de la aplicación no puede llevarlos. `MODE` y `DATABASE_TO_LOWER` sí están permitidos, y tienen que ir siempre porque H2 no los guarda en el archivo.

### Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:h2:file:./.data/xperience;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE` | Base; la aplicación le agrega `;IFEXISTS=TRUE` |
| `DB_OWNER_USER` / `DB_OWNER_PASSWORD` | `sa` / vacía | Credenciales de Flyway |
| `DB_USER` | `xperience_app` | Usuario de la aplicación |
| `DB_PASSWORD` | `app-solo-desarrollo` en el perfil `dev`; **sin valor fuera de `dev`** | Contraseña del usuario de la aplicación |

En local no hay pasos nuevos: `java -jar target/xperience.jar --spring.profiles.active=dev` crea el usuario sobre la base existente. `scripts/crear-empresa` funciona igual: corre con el perfil `dev` e inserta con el usuario limitado.

`scripts/demo-permiso-asignable.sql` escribe en `permiso`, así que ahora se ejecuta con el dueño (`sa`), por ejemplo desde la consola de H2. Con las credenciales de la aplicación falla, y eso es justamente lo que se busca demostrar.

## Pruebas

`DatabaseLeastPrivilegeTest`, con la base de pruebas, usuario `xperience_app` y dueño `sa`:

1. El pool de la aplicación se conecta como `xperience_app`, no como el dueño, y ese usuario no es administrador.
2. Con las credenciales de la aplicación fallan **por permisos** (códigos H2 90096 y 90040, no por sintaxis): `DROP TABLE`, `ALTER TABLE`, `CREATE TABLE … AS SELECT`, `CREATE INDEX`, `CREATE USER`, `ALTER USER … ADMIN TRUE`, `GRANT`, `FILE_READ`, `CREATE ALIAS` y `SCRIPT`.
3. `permiso` se puede leer, pero fallan `INSERT` (por ejemplo, darle `staff.cambiar_rol` a `TALENTO`), `UPDATE`, `DELETE` y `TRUNCATE`; la cantidad de filas no cambia.
4. `alta_empresa` se puede leer, pero no actualizar ni borrar.
5. `flyway_schema_history` no se puede leer ni borrar.
6. Para **todas** las tablas del esquema, los privilegios otorgados son exactamente los esperados. Si una migración agrega una tabla, la prueba indica qué privilegios recibió.
7. Si el dueño otorga a mano `INSERT` sobre `permiso` y rol de administrador, volver a ajustar los privilegios los revoca.
8. Ajustar los privilegios rechaza usar al dueño como usuario de la aplicación, una contraseña vacía y un nombre de usuario con SQL.

El resto de la suite corre con el usuario limitado, lo que comprueba que alcanza para toda la aplicación. `AuthIntegrationTest` (RS16) modifica `permiso` con una conexión del dueño, porque cambiar el catálogo ya no es algo que la aplicación pueda hacer.

```bash
cd backend
./mvnw "-Dtest=DatabaseLeastPrivilegeTest" test
```

## Fuera de alcance

- **`sa` sin contraseña:** es el dueño de la base local de desarrollo existente. Fuera de local, `DB_OWNER_PASSWORD` debe tener valor.
