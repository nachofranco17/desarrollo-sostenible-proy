-- RF5: cursos y proyectos de las empresas. Toda tabla vinculada a una empresa lleva empresa_id,
-- que el servidor copia de la sesión al crear el registro (ver docs/seguridad/modelo-autorizacion.md).
CREATE TABLE curso (
    id UUID PRIMARY KEY,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('CURSO', 'PROYECTO')),
    titulo VARCHAR(150) NOT NULL,
    descripcion VARCHAR(4000) NOT NULL,
    tecnologia VARCHAR(60) NOT NULL,
    nivel VARCHAR(12) NOT NULL CHECK (nivel IN ('INICIAL', 'INTERMEDIO', 'AVANZADO')),
    duracion_horas INTEGER NOT NULL CHECK (duracion_horas BETWEEN 1 AND 1000),
    costo NUMERIC(10, 2) NOT NULL CHECK (costo >= 0),
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('BORRADOR', 'PUBLICADO', 'BAJADO')),
    creado_por UUID NOT NULL REFERENCES usuario(id),
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_curso_empresa ON curso(empresa_id);

CREATE TABLE leccion (
    id UUID PRIMARY KEY,
    curso_id UUID NOT NULL REFERENCES curso(id) ON DELETE CASCADE,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    numero INTEGER NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    cuerpo VARCHAR(20000) NOT NULL
);
CREATE INDEX ix_leccion_curso ON leccion(curso_id);

CREATE TABLE entregable (
    id UUID PRIMARY KEY,
    curso_id UUID NOT NULL REFERENCES curso(id) ON DELETE CASCADE,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    numero INTEGER NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    consigna VARCHAR(4000) NOT NULL,
    pista VARCHAR(500) NOT NULL
);
CREATE INDEX ix_entregable_curso ON entregable(curso_id);

-- Hash del PasswordEncoder sobre la respuesta normalizada. Se reemplazan, nunca se leen (R2).
CREATE TABLE respuesta_aceptada (
    id UUID PRIMARY KEY,
    entregable_id UUID NOT NULL REFERENCES entregable(id) ON DELETE CASCADE,
    hash VARCHAR(255) NOT NULL
);
CREATE INDEX ix_respuesta_entregable ON respuesta_aceptada(entregable_id);

-- El archivo vive en un directorio privado; sólo se entrega a través del backend.
CREATE TABLE material (
    id UUID PRIMARY KEY,
    curso_id UUID NOT NULL REFERENCES curso(id) ON DELETE CASCADE,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    nombre_original VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    tamanio_bytes BIGINT NOT NULL,
    clave_almacen VARCHAR(36) NOT NULL UNIQUE,
    subido_por UUID NOT NULL REFERENCES usuario(id),
    subido_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_material_curso ON material(curso_id);
