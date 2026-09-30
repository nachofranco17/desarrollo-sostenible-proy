CREATE TABLE perfil_talento (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id),
    telefono VARCHAR(30),
    especializaciones TEXT NOT NULL DEFAULT '[]',
    notificar_novedades_cursos BOOLEAN NOT NULL DEFAULT TRUE,
    notificar_ofertas BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO perfil_talento (usuario_id)
SELECT id FROM usuario WHERE tipo_cuenta = 'TALENTO';

INSERT INTO permiso (rol, accion) VALUES
    ('TALENTO', 'perfil.gestionar'),
    ('TALENTO', 'cuenta.gestionar');
