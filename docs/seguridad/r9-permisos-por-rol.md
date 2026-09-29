# R9 — Mínimo privilegio por rol

> Cada consumidor debe poder ejecutar únicamente el conjunto de operaciones que la matriz de R1 define
> para su rol, y ninguna otra.

Implementado en [`PermisosPorRol`](../../backend/src/main/java/uy/edu/um/xperience/seguridad/PermisosPorRol.java)
y verificado por `PermisosPorRolTest` y `GestionContenidoTest.MinimoPrivilegio`.

## Operaciones por rol

| Acción                  | Administrador | Editor de contenido | Reclutador | Talento | Staff sin rol |
|-------------------------|:-------------:|:-------------------:|:----------:|:-------:|:-------------:|
| `CONTENIDO_GESTION_VER` | ✔             | ✔                   | —          | —       | —             |
| `CONTENIDO_CREAR`       | ✔             | ✔                   | —          | —       | —             |
| `CONTENIDO_EDITAR`      | ✔             | ✔                   | —          | —       | —             |
| `CONTENIDO_PUBLICAR`    | ✔             | ✔                   | —          | —       | —             |
| `CONTENIDO_BAJAR`       | ✔             | ✔                   | —          | —       | —             |
| `CONTENIDO_ELIMINAR`    | ✔             | ✔                   | —          | —       | —             |
| `MATERIAL_SUBIR`        | ✔             | ✔                   | —          | —       | —             |
| `MATERIAL_DESCARGAR`    | ✔             | ✔                   | —          | —       | —             |
| `MATERIAL_ELIMINAR`     | ✔             | ✔                   | —          | —       | —             |
| `OFERTA_GESTIONAR`      | ✔             | —                   | ✔          | —       | —             |
| `POSTULANTES_VER`       | ✔             | —                   | ✔          | —       | —             |
| `STAFF_INVITAR`         | ✔             | —                   | —          | —       | —             |
| `STAFF_ASIGNAR_ROL`     | ✔             | —                   | —          | —       | —             |

Las acciones de ofertas y staff las usarán RF6 y RF7; se definen acá porque R9 fija quién puede
ejecutarlas. Las operaciones propias del Talento (catálogo, inscripción, postulación) se agregan con
sus requerimientos funcionales.

## Reglas

- Lo que no figura en la tabla está denegado. No hay ningún rol genérico con acceso total.
- El staff sin rol asignado no tiene ninguna operación.
- Cada operación de RF5 verifica el rol antes de tocar datos; si no corresponde, responde
  `403 PROHIBIDO` sin aplicar ningún cambio.
- Este requerimiento cubre el eje vertical (qué operaciones ejecuta cada rol). Que un usuario no
  alcance datos de otra empresa es el eje horizontal y corresponde a R15.

## Dependencia con RF1

`PermisosPorRol` recibe el rol a partir del `Consumidor` (usuario, rol y empresa), que el inicio de
sesión (RF1) debe dejar como principal del `Authentication` de Spring Security. Hasta que exista,
toda llamada a la API responde `401`.
