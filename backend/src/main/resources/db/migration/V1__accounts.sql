CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    correo VARCHAR(254) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    apellido VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    tipo_cuenta VARCHAR(10) NOT NULL CHECK (tipo_cuenta IN ('TALENTO', 'STAFF')),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (correo = LOWER(TRIM(correo)))
);
CREATE TABLE empresa (
    id UUID PRIMARY KEY,
    nombre VARCHAR(160) NOT NULL
);
CREATE TABLE membresia (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id),
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    rol VARCHAR(20) CHECK (rol IN ('ADMIN', 'RECLUTADOR', 'EDITOR')),
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('PENDIENTE', 'ACTIVA', 'BAJA'))
);
CREATE TABLE permiso (
    rol VARCHAR(20) NOT NULL,
    accion VARCHAR(80) NOT NULL,
    PRIMARY KEY (rol, accion)
);
INSERT INTO permiso (rol, accion) VALUES
    ('TALENTO', 'cuenta.ver'), ('ADMIN', 'cuenta.ver'),
    ('RECLUTADOR', 'cuenta.ver'), ('EDITOR', 'cuenta.ver');
CREATE TABLE alta_empresa (
    id UUID PRIMARY KEY,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    administrador_id UUID NOT NULL REFERENCES usuario(id),
    ejecutor VARCHAR(160) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
