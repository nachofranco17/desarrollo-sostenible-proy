-- RS23: deletion is separate from reading the account and requires a fresh session proof.
-- Admin self-deletion is forbidden by the governance invariant; it receives no grant.
INSERT INTO permiso (rol, accion, alcance) VALUES
    ('TALENTO', 'cuenta.eliminar', 'PROPIO'),
    ('RECLUTADOR', 'cuenta.eliminar', 'PROPIO'),
    ('EDITOR', 'cuenta.eliminar', 'PROPIO'),
    ('TALENTO', 'sesion.reautenticar', 'PROPIO'),
    ('RECLUTADOR', 'sesion.reautenticar', 'PROPIO'),
    ('EDITOR', 'sesion.reautenticar', 'PROPIO');
