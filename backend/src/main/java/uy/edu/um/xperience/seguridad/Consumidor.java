package uy.edu.um.xperience.seguridad;

import java.util.UUID;

/**
 * Quién origina la solicitud. La autenticación (RF1) debe dejarlo como principal del
 * {@code Authentication} en el contexto de Spring Security; los controladores lo reciben como
 * parámetro. El rol y la empresa son nulos cuando no corresponden (Talento, o staff sin rol).
 */
public record Consumidor(UUID usuarioId, Rol rol, UUID empresaId) {
}
