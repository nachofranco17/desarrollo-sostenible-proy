-- RF5: cursos y proyectos de las empresas.
-- empresa_id, creado_por y subido_por quedan sin clave foránea hasta que RF1/RF7 creen las tablas
-- de empresas y usuarios.

-- Cursos y proyectos comparten la misma entidad; el tipo decide si lleva lecciones o entregables.
CREATE TABLE contenido (
    id              UUID PRIMARY KEY,
    empresa_id      UUID NOT NULL,
    tipo            VARCHAR(10)  NOT NULL,   -- CURSO | PROYECTO
    titulo          VARCHAR(150) NOT NULL,
    descripcion     VARCHAR(4000) NOT NULL,
    tecnologia      VARCHAR(60)  NOT NULL,
    nivel           VARCHAR(20)  NOT NULL,   -- INICIAL | INTERMEDIO | AVANZADO
    duracion_horas  INTEGER      NOT NULL,
    costo           NUMERIC(10, 2) NOT NULL,
    estado          VARCHAR(12)  NOT NULL,   -- BORRADOR | PUBLICADO | BAJADO
    creado_por      UUID NOT NULL,
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

-- Las respuestas aceptadas se guardan sólo como hash de su forma normalizada.
CREATE TABLE respuesta_aceptada (
    id             UUID PRIMARY KEY,
    entregable_id  UUID NOT NULL REFERENCES entregable (id) ON DELETE CASCADE,
    hash           VARCHAR(64) NOT NULL
);
CREATE INDEX ix_respuesta_entregable ON respuesta_aceptada (entregable_id);

CREATE TABLE material (
    id               UUID PRIMARY KEY,
    contenido_id     UUID NOT NULL REFERENCES contenido (id) ON DELETE CASCADE,
    nombre_original  VARCHAR(255) NOT NULL,
    tipo_mime        VARCHAR(100) NOT NULL,
    tamanio_bytes    BIGINT NOT NULL,
    clave_almacen    VARCHAR(64) NOT NULL UNIQUE,
    subido_por       UUID NOT NULL,
    subido_en        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_material_contenido ON material (contenido_id);
