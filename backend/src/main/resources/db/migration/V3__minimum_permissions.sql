ALTER TABLE permiso ADD COLUMN alcance VARCHAR(20) NOT NULL DEFAULT 'PROPIO';
ALTER TABLE permiso ADD CONSTRAINT permiso_alcance_check CHECK (alcance IN ('PROPIO', 'PUBLICADO', 'ORG'));

-- Account access is technical session access, not a company permission.
DELETE FROM permiso WHERE accion = 'cuenta.ver';
INSERT INTO permiso (rol, accion, alcance) VALUES ('AUTENTICADO', 'cuenta.ver', 'PROPIO');

-- This is the complete initial business permission set for a new Talento.
INSERT INTO permiso (rol, accion, alcance) VALUES
    ('TALENTO', 'catalogo.ver', 'PUBLICADO'),
    ('TALENTO', 'perfil.gestionar', 'PROPIO'),
    ('TALENTO', 'inscripcion.crear', 'PUBLICADO'),
    ('TALENTO', 'postulacion.crear', 'PUBLICADO');

-- Staff permissions apply only after explicit role assignment, within its company.
INSERT INTO permiso (rol, accion, alcance) VALUES
    ('RECLUTADOR', 'oferta.gestionar', 'ORG'),
    ('RECLUTADOR', 'postulacion.ver_listado', 'ORG'),
    ('RECLUTADOR', 'postulacion.cambiar_estado', 'ORG'),
    ('EDITOR', 'curso.gestionar', 'ORG'),
    ('ADMIN', 'oferta.gestionar', 'ORG'),
    ('ADMIN', 'postulacion.ver_listado', 'ORG'),
    ('ADMIN', 'postulacion.cambiar_estado', 'ORG'),
    ('ADMIN', 'curso.gestionar', 'ORG'),
    ('ADMIN', 'staff.cambiar_rol', 'ORG');

-- Null remains the default: accepting an invitation does not assign a role.
ALTER TABLE membresia ALTER COLUMN rol SET DEFAULT NULL;
