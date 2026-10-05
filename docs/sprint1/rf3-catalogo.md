# RF3 — Catálogo de cursos y proyectos

Catálogo público de cursos y proyectos **publicados**. Reutiliza el dominio de RF5 (`curso`) y el permiso `catalogo.ver` (alcance PUBLICADO) ya cargado en la matriz.

## Qué quedó hecho

- Vista `/catalogo` (sin login) con listado conjunto de cursos y proyectos publicados.
- Buscador por nombre (`q` o `nombre`).
- Filtros: tecnología, nivel, duración (rango en horas), costo (rango).
- Combinación AND de todos los filtros activos.
- Botón **Limpiar filtros** que restaura el catálogo completo.
- Detalle informativo en `/catalogo/:id` (sin inscripción ni materiales).

## API

Base: `/api/catalog/courses`. Acción exigida: `catalogo.ver` (Visitante y todos los roles con el permiso).

| Método y ruta | Resultado |
|---|---|
| `GET /api/catalog/courses` | 200: `{ items, tecnologiasDisponibles }` |
| `GET /api/catalog/courses/{id}` | 200: detalle público; borrador/bajado/inexistente → 403 |

### Query params (allowlist)

| Param | Significado | Validación |
|---|---|---|
| `q` / `nombre` | Buscar en el título (substring, case-insensitive, trim) | Máx. 150 caracteres |
| `tecnologia` | Igualdad case-insensitive (texto libre del modelo, máx. 60) | Máx. 60; vacío = sin filtro |
| `nivel` | Exacto | Solo `INICIAL`, `INTERMEDIO`, `AVANZADO` |
| `duracionMin` / `duracionMax` | Rango de horas | Enteros 1–1000; min ≤ max |
| `costoMin` / `costoMax` | Rango de costo | 0.00–100000.00, ≤2 decimales; min ≤ max |

Parámetros desconocidos se **ignoran** (no alteran la consulta). Valores inválidos de la allowlist → **400** con `{ "message": "..." }`.

La búsqueda escapa `%`, `_` y `\` antes del `LIKE` parametrizado. No se concatena SQL con el texto del usuario.

Solo se listan filas con `estado = 'PUBLICADO'`. Borradores y dados de baja no aparecen.

### Respuesta de listado

Cada ítem: `id`, `tipo` (`CURSO`/`PROYECTO`), `titulo`, `tecnologia`, `nivel`, `duracionHoras`, `costo`, `empresaNombre`.

`tecnologiasDisponibles`: valores distintos de tecnología en lo publicado (para el selector).

### Detalle

Mismos campos del ítem más `descripcion`. Sin lecciones, entregables, materiales ni respuestas aceptadas.

## Frontend

- Rutas: `/catalogo`, `/catalogo/:id`.
- Enlaces desde ingreso/registro y Mi cuenta.
- Formulario de filtros + **Aplicar** / **Limpiar**.
- Mensaje explícito cuando no hay resultados.

## Pruebas

`CatalogIntegrationTest` cubre acceso anónimo, cursos y proyectos publicados, búsqueda, cada filtro, combinación de dos y de todos, “limpiar” (consulta sin filtros), detalle de curso y proyecto, tecnología/nivel/rangos/búsqueda inválidos, y ocultamiento de borradores.

```bash
cd backend
./mvnw '-Dtest=CatalogIntegrationTest' test
```

## Cómo probar manualmente

1. Con el backend `dev` y el frontend, abrir http://127.0.0.1:5173/catalogo (sin login).
2. Si no hay publicados: ingresar como Admin/Editor, crear y **publicar** un curso y un proyecto en `/gestion`.
3. Buscar por parte del nombre → aplicar filtros.
4. Filtrar por tecnología, nivel, duración y costo (probar costo `0` para gratuitos).
5. Combinar varios filtros.
6. Abrir **Ver** en un ítem y comprobar título, descripción, tecnología, nivel, duración, costo, tipo y empresa.
7. **Limpiar filtros** → vuelve el listado completo de publicados.
