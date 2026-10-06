-- RF6 etapa 1. Los permisos existentes no cambian.
CREATE TABLE oferta (
    id UUID PRIMARY KEY,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    titulo VARCHAR(150) NOT NULL CHECK (LENGTH(TRIM(titulo)) > 0),
    descripcion VARCHAR(4000) NOT NULL CHECK (LENGTH(TRIM(descripcion)) > 0),
    estado VARCHAR(10) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'PUBLICADA')),
    creado_por UUID NOT NULL REFERENCES usuario(id),
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, empresa_id)
);
CREATE INDEX ix_oferta_empresa ON oferta(empresa_id, actualizado_en);
CREATE INDEX ix_oferta_publicada ON oferta(estado, creado_en);

CREATE TABLE oferta_requisito (
    oferta_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    curso_id UUID NOT NULL REFERENCES curso(id),
    PRIMARY KEY (oferta_id, curso_id),
    FOREIGN KEY (oferta_id, empresa_id) REFERENCES oferta(id, empresa_id)
);
CREATE INDEX ix_oferta_requisito_curso ON oferta_requisito(curso_id);

-- Cada subida crea una versión; el puntero del perfil no modifica versiones anteriores.
CREATE TABLE curriculum (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES perfil_talento(usuario_id),
    nombre_original VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL CHECK (tipo_mime = 'application/pdf'),
    tamanio_bytes BIGINT NOT NULL CHECK (tamanio_bytes BETWEEN 1 AND 5242880),
    clave_almacen VARCHAR(36) NOT NULL UNIQUE,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, usuario_id)
);
CREATE INDEX ix_curriculum_usuario ON curriculum(usuario_id, creado_en);
ALTER TABLE perfil_talento ADD COLUMN curriculum_actual_id UUID;
ALTER TABLE perfil_talento ADD CONSTRAINT fk_perfil_curriculum_propio
    FOREIGN KEY (curriculum_actual_id, usuario_id) REFERENCES curriculum(id, usuario_id);

CREATE TABLE postulacion (
    id UUID PRIMARY KEY,
    talento_id UUID NOT NULL REFERENCES perfil_talento(usuario_id),
    oferta_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    curriculum_id UUID NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ENVIADA' CHECK (estado = 'ENVIADA'),
    fecha TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (oferta_id, talento_id),
    FOREIGN KEY (oferta_id, empresa_id) REFERENCES oferta(id, empresa_id),
    FOREIGN KEY (curriculum_id, talento_id) REFERENCES curriculum(id, usuario_id)
);
CREATE INDEX ix_postulacion_oferta_empresa ON postulacion(empresa_id, oferta_id, fecha);
CREATE INDEX ix_postulacion_talento ON postulacion(talento_id, fecha);
