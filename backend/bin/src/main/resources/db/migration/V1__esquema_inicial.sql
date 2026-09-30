-- Identificadores UUID en todas las entidades (R13): no hay enteros consecutivos que enumerar.

CREATE TABLE empresa (
    id          UUID PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL UNIQUE,
    creada_en   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE usuario (
    id             UUID PRIMARY KEY,
    email          VARCHAR(254) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    tipo           VARCHAR(10)  NOT NULL,   -- TALENTO | STAFF
    activo         BOOLEAN      NOT NULL,
    creado_en      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Cada miembro del staff pertenece a una sola empresa y tiene un único rol (RF7).
-- El rol es nulo mientras el Administrador no lo asigne (R7).
CREATE TABLE membresia (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL UNIQUE REFERENCES usuario (id),
    empresa_id  UUID NOT NULL REFERENCES empresa (id),
    rol         VARCHAR(30)
);

-- Permisos como dato (R4): rol + acción + alcance. Lo que no figura acá está denegado (R6).
CREATE TABLE permiso (
    rol      VARCHAR(30) NOT NULL,
    accion   VARCHAR(60) NOT NULL,
    alcance  VARCHAR(30) NOT NULL,
    PRIMARY KEY (rol, accion)
);

-- Cursos y proyectos comparten la misma entidad; el tipo decide si lleva lecciones o entregables.
CREATE TABLE contenido (
    id              UUID PRIMARY KEY,
    empresa_id      UUID NOT NULL REFERENCES empresa (id),
    tipo            VARCHAR(10)  NOT NULL,   -- CURSO | PROYECTO
    titulo          VARCHAR(150) NOT NULL,
    descripcion     VARCHAR(4000) NOT NULL,
    tecnologia      VARCHAR(60)  NOT NULL,
    nivel           VARCHAR(20)  NOT NULL,   -- INICIAL | INTERMEDIO | AVANZADO
    duracion_horas  INTEGER      NOT NULL,
    costo           NUMERIC(10, 2) NOT NULL,
    estado          VARCHAR(12)  NOT NULL,   -- BORRADOR | PUBLICADO | BAJADO
    creado_por      UUID NOT NULL REFERENCES usuario (id),
    creado_en       TIMESTAMP WITH TIME ZONE NOT NULL,
    actualizado_en  TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT NOT NULL
);
CREATE INDEX ix_contenido_empresa ON contenido (empresa_id);
CREATE INDEX ix_contenido_estado ON contenido (estado);

CREATE TABLE leccion (
    id            UUID PRIMARY KEY,
    contenido_id  UUID NOT NULL REFERENCES contenido (id) ON DELETE CASCADE,
    numero        INTEGER NOT NULL,
    titulo        VARCHAR(150) NOT NULL,
    cuerpo        VARCHAR(20000) NOT NULL
);
CREATE INDEX ix_leccion_contenido ON leccion (contenido_id);

CREATE TABLE entregable (
    id            UUID PRIMARY KEY,
    contenido_id  UUID NOT NULL REFERENCES contenido (id) ON DELETE CASCADE,
    numero        INTEGER NOT NULL,
    titulo        VARCHAR(150) NOT NULL,
    consigna      VARCHAR(4000) NOT NULL,
    pista         VARCHAR(500) NOT NULL
);
CREATE INDEX ix_entregable_contenido ON entregable (contenido_id);

-- Las respuestas aceptadas se guardan sólo como HMAC de su forma normalizada (R2): nadie las lee.
CREATE TABLE respuesta_aceptada (
    id             UUID PRIMARY KEY,
    entregable_id  UUID NOT NULL REFERENCES entregable (id) ON DELETE CASCADE,
    hash           VARCHAR(64) NOT NULL
);
CREATE INDEX ix_respuesta_entregable ON respuesta_aceptada (entregable_id);

-- El archivo vive fuera de cualquier carpeta pública; se sirve sólo a través del PEP (R14).
CREATE TABLE material (
    id               UUID PRIMARY KEY,
    contenido_id     UUID NOT NULL REFERENCES contenido (id) ON DELETE CASCADE,
    nombre_original  VARCHAR(255) NOT NULL,
    tipo_mime        VARCHAR(100) NOT NULL,
    tamanio_bytes    BIGINT NOT NULL,
    clave_almacen    VARCHAR(64) NOT NULL UNIQUE,
    subido_por       UUID NOT NULL REFERENCES usuario (id),
    subido_en        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_material_contenido ON material (contenido_id);
