-- RF4: inscripción del Talento a un curso/proyecto y avance persistido (porcentaje).
-- Unicidad (talento_id, curso_id) evita inscripciones duplicadas bajo concurrencia.
CREATE TABLE inscripcion (
    id UUID PRIMARY KEY,
    talento_id UUID NOT NULL REFERENCES usuario(id),
    curso_id UUID NOT NULL REFERENCES curso(id),
    fecha TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    porcentaje_avance INTEGER NOT NULL DEFAULT 0
        CHECK (porcentaje_avance BETWEEN 0 AND 100),
    CONSTRAINT uq_inscripcion_talento_curso UNIQUE (talento_id, curso_id)
);
CREATE INDEX ix_inscripcion_talento ON inscripcion(talento_id);
CREATE INDEX ix_inscripcion_curso ON inscripcion(curso_id);
