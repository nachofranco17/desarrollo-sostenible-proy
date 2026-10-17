# RS14 — Archivos sujetos a las mismas reglas de autorización

Requerimiento de seguridad: **RS14** (ASVS v5.0.0-5.3.1, 5.4.1, 8.2.2; documentado como R14 en el modelo)
Versión: 1.0 (16/10/2026)

Los archivos que gestiona la aplicación (material de cursos y currículums) están sujetos a las mismas reglas de autorización que el resto de los recursos. Ningún archivo se entrega tal cual está guardado: toda descarga pasa por un endpoint del backend que atraviesa el PEP, y el almacenamiento no es accesible desde el navegador. Mitiga la amenaza **A4** (CWE-552, CWE-425).

## Endpoints de archivos

| Archivo | Endpoint | Acción (PEP) | Autorización de instancia |
|---|---|---|---|
| Material de curso/proyecto | `GET /api/company/courses/{id}/materials/{materialId}` | `material.descargar` | Curso buscado por empresa de la sesión **o** inscripción del Talento de la sesión (`INSCRIPTO`), `autorizar` sobre el curso y material buscado junto con el curso y la empresa |
| CV propio | `GET /api/profile/me/curriculum` | `curriculum.descargar` | CV actual del Talento de la sesión (`PROPIO`); la ruta no recibe identificador |
| CV de un postulante | `GET /api/company/offers/{id}/applications/{applicationId}/curriculum` | `curriculum.descargar` | Postulación buscada por oferta + empresa de la sesión, `POSTULANTE` sobre la relación persistida y la versión del CV adjunta a esa postulación |

Recurso ajeno e inexistente producen la misma denegación (403), y el intento se registra como `FILE_ACCESS_DENIED` (RS18).

No hay certificados ni imágenes de perfil implementados. Cuando se agreguen, deben seguir el mismo patrón: un endpoint con acción del catálogo, consulta acotada por la sesión y `autorizar`. La prueba de la sección siguiente obliga a revisarlo: falla si aparece un endpoint que devuelve un archivo y no está en la lista.

## Almacenamiento

- **Directorio privado** (`STORAGE_DIR`, por defecto `./.data/archivos`, ignorado por git). No está dentro de ningún directorio que sirva el backend ni el servidor del frontend.
- **Nombres opacos:** cada archivo se guarda con un UUID generado por `FileStorage`, sin extensión ni datos del cliente. `FileStorage` solo acepta UUID canónicos y verifica que la ruta resuelta quede dentro del directorio, así que la clave no puede usarse para recorrer el disco.
- **La clave nunca se expone.** No aparece en ninguna respuesta de la API ni en los registros. No hay URL directa ni enlaces firmados: se descartaron porque la descarga no atravesaría el PEP y el enlace funcionaría como credencial al portador.
- **Sin recursos estáticos en el backend:** `spring.web.resources.add-mappings=false`. El backend solo atiende la API; además, todo lo que esté fuera de `/api` se deniega (`denyAll`).
- **Servidor del frontend:** Vite sirve únicamente `frontend/`, con `server.fs.strict` y `**/.data/**` en la lista de denegación. En producción el servidor estático entrega solo `frontend/dist`.

## Cómo se entrega cada archivo (ASVS 5.3.1, 5.4.1)

- **Tipo decidido por el servidor:** se detecta por la firma de los primeros bytes al subir (PDF, PNG, JPG, ZIP, MP4 para material; solo PDF para CV). El `Content-Type` y la extensión que manda el cliente se ignoran.
- **Nombre controlado por el servidor:** se toma solo el último segmento, se quitan caracteres de control y comillas, se acota a 255 caracteres y **se fuerza la extensión del tipo detectado**. Un PDF subido como `pagina.html` se descarga como `pagina.html.pdf`.
- **Encabezados de la respuesta:**
  - `Content-Disposition: attachment` con el nombre codificado según RFC 6266.
  - `Content-Type` del tipo detectado.
  - `X-Content-Type-Options: nosniff`.
  - `Cache-Control: no-store`.

  El navegador nunca interpreta un archivo subido como página, y el archivo no queda en caché.
- **Lo subido nunca se ejecuta:** queda fuera de cualquier carpeta pública, sin extensión, y solo se lee como bytes para copiarlo a la respuesta.

## Pruebas

`Rs14FileAuthorizationTest`:

1. **Material:** lo descargan el Editor y el Administrador de la empresa y el Talento inscripto. El Reclutador, las cuentas de otra empresa y los Talentos no inscriptos reciben 403, con el mismo cuerpo que un material inexistente. El visitante recibe 401.
2. **CV:** el propio solo lo descarga su dueño, por `/me`. El de la postulación lo descargan el Administrador y el Reclutador de la empresa de la oferta. El Editor, la otra empresa, el propio Talento por la ruta empresarial y los demás Talentos reciben 403. El visitante recibe 401.
3. **Clave de almacenamiento:**
   - Es un UUID canónico sin extensión y no aparece en ninguna respuesta.
   - Pedir el archivo por su ubicación (`/archivos/<clave>`, `/.data/archivos/<clave>`, `/api/files/<clave>`, `..%2F`, `%2e%2e`, etc.) nunca devuelve el contenido, ni siquiera al dueño.
   - Usada como id de material o de postulación, se deniega igual que un UUID inventado.
4. El backend no registra manejadores de recursos estáticos.
5. Las tres descargas responden con el tipo detectado, `attachment`, `nosniff`, `no-store` y extensión `.pdf`, aunque el archivo se haya subido como `.html` con `Content-Type: text/html`.
6. **Guarda estática:** los endpoints que devuelven archivos (`Resource`, `byte[]`, `InputStream`, `StreamingResponseBody`) son exactamente los tres de la tabla, con su acción. Un endpoint nuevo de archivos hace fallar la prueba hasta que se revise y se agregue.

Cobertura relacionada ya existente: `CourseIntegrationTest` (subida, tipos no admitidos, inscripción y baja del curso), `CurriculumIntegrationTest` (firma PDF, tamaño, nombres, versiones), `ApplicationIntegrationTest` (CV conservado por postulación) y `Rs18DeniedAccessLogTest` (`FILE_ACCESS_DENIED` sin contenido ni URL).

```bash
cd backend
./mvnw "-Dtest=Rs14FileAuthorizationTest" test
```

## Cómo probar manualmente

1. Como Editor, subir un PDF a un curso y anotar el id del material. Como Reclutador de la misma empresa, abrir `/api/company/courses/{curso}/materials/{material}` → 403.
2. Como Talento sin inscripción, la misma URL → 403. Inscribirse al curso publicado y repetir → descarga.
3. Buscar en la base la `clave_almacen` del material e intentar `/api/files/<clave>`, `/.data/archivos/<clave>` o `http://localhost:5173/@fs/<ruta absoluta del archivo>` → ninguno devuelve el archivo.
4. Subir un PDF renombrado como `pagina.html` → la descarga se ofrece como `pagina.html.pdf`, con `Content-Disposition: attachment`.
