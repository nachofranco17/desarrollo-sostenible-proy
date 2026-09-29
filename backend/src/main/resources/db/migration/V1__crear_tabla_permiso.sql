-- Un permiso combina un rol, una accion y un alcance (ver docs/seguridad/modelo-autorizacion.md).
-- Si no existe una fila para un rol y una accion, el acceso esta denegado (R6).
CREATE TABLE permiso (
    id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rol     VARCHAR(20) NOT NULL,
    accion  VARCHAR(50) NOT NULL,
    alcance VARCHAR(20) NOT NULL,
    CONSTRAINT uq_permiso_rol_accion UNIQUE (rol, accion),
    CONSTRAINT ck_permiso_rol CHECK (rol IN ('VISITANTE', 'TALENTO', 'ADMIN', 'RECLUTADOR', 'EDITOR')),
    CONSTRAINT ck_permiso_alcance CHECK (alcance IN ('GLOBAL', 'PUBLICADO', 'PROPIO', 'ORG', 'POSTULANTE', 'INSCRIPTO'))
);
