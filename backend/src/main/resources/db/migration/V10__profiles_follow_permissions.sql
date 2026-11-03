-- perfil_talento es el recurso personal que respalda perfil y curriculum. La autorización para
-- usarlo pertenece al catálogo permiso; el tipo de cuenta no debe actuar como un segundo PEP.
INSERT INTO perfil_talento (usuario_id)
SELECT u.id
FROM usuario u
WHERE NOT EXISTS (
    SELECT 1 FROM perfil_talento p WHERE p.usuario_id = u.id
);
