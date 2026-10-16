-- Demo en vivo: "Deny by default — ruta con permiso no asignado"
-- Acción: demo.permiso_asignable (está en el catálogo y en @RequiereAccion, pero V4 no carga filas).
--
-- 1) Sin estas filas → GET /api/demo/permiso-asignable → 403
-- 2) Ejecutá el INSERT del rol con el que estás logueada → 200
-- 3) Al terminar, ejecutá el DELETE para volver al estado deny-by-default

-- Habilitar: el ROL debe coincidir con la sesión (en Mi cuenta: Talento → TALENTO).
-- También hace falta rebuild del jar si acabás de agregar DemoController.
INSERT INTO permiso (rol, accion, alcance) VALUES ('TALENTO', 'demo.permiso_asignable', 'GLOBAL');
-- INSERT INTO permiso (rol, accion, alcance) VALUES ('ADMIN', 'demo.permiso_asignable', 'GLOBAL');

-- Volver a denegar
-- DELETE FROM permiso WHERE accion = 'demo.permiso_asignable';
