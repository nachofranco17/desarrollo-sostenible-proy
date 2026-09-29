package com.xperience.backend.seguridad;

import java.util.UUID;

public record Sujeto(UUID usuarioId, TipoCuenta tipoCuenta, UUID empresaId, Rol rol) {
}
